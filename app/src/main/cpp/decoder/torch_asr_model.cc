#include "decoder/torch_asr_model.h"

#include <memory>
#include <utility>

#include "torch/script.h"
#include "torch/torch.h"

namespace asr_cc {

void TorchAsrModel::Read(const std::string& model_path, const int num_threads) {
  torch::jit::script::Module model = torch::jit::load(model_path);
  module_ = std::make_shared<TorchModule>(std::move(model));
  // For multi-thread performance
  at::set_num_threads(num_threads);
#ifndef ANDROID
  at::set_num_interop_threads(1);
#endif
  torch::NoGradGuard no_grad;
  module_->eval();
  torch::jit::IValue o1 = module_->run_method("subsampling_rate");
  CHECK_EQ(o1.isInt(), true);
  subsampling_rate_ = o1.toInt();
  torch::jit::IValue o2 = module_->run_method("right_context");
  CHECK_EQ(o2.isInt(), true);
  right_context_ = o2.toInt();
  torch::jit::IValue o3 = module_->run_method("sos_symbol");
  CHECK_EQ(o3.isInt(), true);
  sos_ = o3.toInt();
  torch::jit::IValue o4 = module_->run_method("eos_symbol");
  CHECK_EQ(o4.isInt(), true);
  eos_ = o4.toInt();
  LOG(INFO) << "torch model info subsampling_rate " << subsampling_rate_
            << " right context " << right_context_ << " sos " << sos_ << " eos "
            << eos_;
}

}  // namespace asr_cc
