#include <jni.h>
#include <string>

#include "glog/logging.h"
#include "torch/script.h"
#include "torch/torch.h"

#include "decoder/symbol_table.h"
#include "decoder/torch_asr_decoder.h"
#include "decoder/torch_asr_model.h"
#include "frontend/feature_pipeline.h"
#include "frontend/wav.h"

namespace asr_cc {

std::shared_ptr<DecodeOptions> decode_config;
std::shared_ptr<FeaturePipelineConfig> feature_config;
std::shared_ptr<FeaturePipeline> feature_pipeline;
std::shared_ptr<SymbolTable> symbol_table;
std::shared_ptr<TorchAsrModel> model;
std::shared_ptr<TorchAsrDecoder> decoder;
bool finished = false;

void init(JNIEnv *env, jobject, jstring jModelPath, jstring jDictPath) {
  model = std::make_shared<TorchAsrModel>();
  const char *pModelPath = (env)->GetStringUTFChars(jModelPath, nullptr);
  std::string modelPath = std::string(pModelPath);
  LOG(INFO) << "model path: " << modelPath;
  model->Read(modelPath);

  const char *pDictPath = (env)->GetStringUTFChars(jDictPath, nullptr);
  std::string dictPath = std::string(pDictPath);
  LOG(INFO) << "dict path: " << dictPath;
  symbol_table = std::make_shared<SymbolTable>(dictPath);

  feature_config = std::make_shared<FeaturePipelineConfig>();
  feature_config->num_bins = 80;
  feature_pipeline = std::make_shared<FeaturePipeline>(*feature_config);

  decode_config = std::make_shared<DecodeOptions>();
  decode_config->chunk_size = 16;

  decoder = std::make_shared<TorchAsrDecoder>(feature_pipeline, model,
                                              *symbol_table, *decode_config);
}

void reset(JNIEnv *env, jobject) {
  LOG(INFO) << "reset";
  decoder->Reset();
  finished = false;
}

void accept_waveform(JNIEnv *env, jobject, jshortArray jWaveform) {
  jsize size = env->GetArrayLength(jWaveform);
  std::vector<int16_t> waveform(size);
  env->GetShortArrayRegion(jWaveform, 0, size, &waveform[0]);
  std::vector<float> floatWaveform(waveform.begin(), waveform.end());
  feature_pipeline->AcceptWaveform(floatWaveform);
//  LOG(INFO) << "accept waveform in ms: "
//            << int(floatWaveform.size() / 16);
}

void set_input_finished() {
  LOG(INFO) << "input finished";
  feature_pipeline->set_input_finished();
}

void decode_thread_func() {
  while (true) {
    bool finish = decoder->Decode();
    if (finish) {
      LOG(INFO) << "final result: " << decoder->result();
      finished = true;
      break;
    } else {
      LOG(INFO) << "partial result: " << decoder->result();
    }
  }
}

void start_decode() {
  std::thread decode_thread(decode_thread_func);
  decode_thread.detach();
}

jboolean get_finished(JNIEnv *env, jobject) {
  if (finished) {
    LOG(INFO) << "recognize finished";
  }
  return finished ? JNI_TRUE : JNI_FALSE;
}

jstring get_result(JNIEnv *env, jobject) {
  LOG(INFO) << "ui result: " << decoder->result();
  return env->NewStringUTF(decoder->result().c_str());
}
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }

  jclass c = env->FindClass("com/myhome/application/Recognize");
  if (c == nullptr) {
    return JNI_ERR;
  }

  static const JNINativeMethod methods[] = {
      {"init", "(Ljava/lang/String;Ljava/lang/String;)V",
       reinterpret_cast<void *>(asr_cc::init)},
      {"reset", "()V", reinterpret_cast<void *>(asr_cc::reset)},
      {"acceptWaveform", "([S)V",
       reinterpret_cast<void *>(asr_cc::accept_waveform)},
      {"setInputFinished", "()V",
       reinterpret_cast<void *>(asr_cc::set_input_finished)},
      {"getFinished", "()Z", reinterpret_cast<void *>(asr_cc::get_finished)},
      {"startDecode", "()V", reinterpret_cast<void *>(asr_cc::start_decode)},
      {"getResult", "()Ljava/lang/String;",
       reinterpret_cast<void *>(asr_cc::get_result)},
  };
  int rc = env->RegisterNatives(c, methods,
                                sizeof(methods) / sizeof(JNINativeMethod));

  if (rc != JNI_OK) {
    return rc;
  }

  return JNI_VERSION_1_6;
}