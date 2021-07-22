#ifndef DECODER_TORCH_ASR_MODEL_H_
#define DECODER_TORCH_ASR_MODEL_H_

#include <memory>
#include <string>

#include "torch/script.h"
#include "torch/torch.h"
#include "utils/utils.h"

namespace asr_cc {

using TorchModule = torch::jit::script::Module;
// A wrapper for pytorch asr model
class TorchAsrModel {
 public:
  TorchAsrModel() = default;

  void Read(const std::string& model_path, int num_threads = 1);
  int right_context() const { return right_context_; }
  int subsampling_rate() const { return subsampling_rate_; }
  int sos() const { return sos_; }
  int eos() const { return eos_; }
  std::shared_ptr<TorchModule> torch_model() const { return module_; }

 private:
  std::shared_ptr<TorchModule> module_ = nullptr;
  int right_context_ = 1;
  int subsampling_rate_ = 1;
  int sos_ = 0;
  int eos_ = 0;

 public:
  DISALLOW_COPY_AND_ASSIGN(TorchAsrModel);
};

}  // namespace asr_cc

#endif  // DECODER_TORCH_ASR_MODEL_H_
