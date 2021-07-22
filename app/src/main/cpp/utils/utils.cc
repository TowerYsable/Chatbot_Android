// Copyright (c) 2021 Mobvoi Inc (Zhendong Peng)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "utils/utils.h"

#include "glog/logging.h"

namespace asr_cc {

void SplitString(const std::string& str, std::vector<std::string>* strs) {
  auto iss = std::istringstream{str};
  std::string result;
  while (iss >> result) {
    strs->push_back(result);
  }
}

float LogAdd(const float& x, const float& y) {
  static float num_min = -std::numeric_limits<float>::max();
  if (x <= num_min) return y;
  if (y <= num_min) return x;
  float xmax = std::max(x, y);
  return std::log(std::exp(x - xmax) + std::exp(y - xmax)) + xmax;
}

void SplitString(const std::string& data, const std::string& delim,
                 std::vector<std::string>* strs) {
  std::vector<std::string>& elems = *strs;
  std::string::size_type pos = 0;
  std::string::size_type len = data.size();
  std::string::size_type delim_len = delim.size();

  if (len == 0) {
    return;
  }

  if (delim_len == 0) {
    elems.push_back(data);
    return;
  }

  while (pos < len) {
    std::string::size_type find_pos = data.find(delim, pos);
    if (find_pos == std::string::npos) {
      elems.push_back(data.substr(pos, len - pos));
      return;
    }

    elems.push_back(data.substr(pos, find_pos - pos));
    pos = find_pos + delim_len;
  }

  return;
}

std::string UTF8CodeToUTF8String(int code) {
  std::ostringstream ostr;
  if (code < 0) {
    LOG(ERROR) << "LabelsToUTF8String: Invalid character found: " << code;
    return ostr.str();
  } else if (code < 0x80) {
    ostr << static_cast<char>(code);
  } else if (code < 0x800) {
    ostr << static_cast<char>((code >> 6) | 0xc0);
    ostr << static_cast<char>((code & 0x3f) | 0x80);
  } else if (code < 0x10000) {
    ostr << static_cast<char>((code >> 12) | 0xe0);
    ostr << static_cast<char>(((code >> 6) & 0x3f) | 0x80);
    ostr << static_cast<char>((code & 0x3f) | 0x80);
  } else if (code < 0x200000) {
    ostr << static_cast<char>((code >> 18) | 0xf0);
    ostr << static_cast<char>(((code >> 12) & 0x3f) | 0x80);
    ostr << static_cast<char>(((code >> 6) & 0x3f) | 0x80);
    ostr << static_cast<char>((code & 0x3f) | 0x80);
  } else if (code < 0x4000000) {
    ostr << static_cast<char>((code >> 24) | 0xf8);
    ostr << static_cast<char>(((code >> 18) & 0x3f) | 0x80);
    ostr << static_cast<char>(((code >> 12) & 0x3f) | 0x80);
    ostr << static_cast<char>(((code >> 6) & 0x3f) | 0x80);
    ostr << static_cast<char>((code & 0x3f) | 0x80);
  } else {
    ostr << static_cast<char>((code >> 30) | 0xfc);
    ostr << static_cast<char>(((code >> 24) & 0x3f) | 0x80);
    ostr << static_cast<char>(((code >> 18) & 0x3f) | 0x80);
    ostr << static_cast<char>(((code >> 12) & 0x3f) | 0x80);
    ostr << static_cast<char>(((code >> 6) & 0x3f) | 0x80);
    ostr << static_cast<char>((code & 0x3f) | 0x80);
  }
  return ostr.str();
}

// Split utf8 string into characters.
bool SplitUTF8String(const std::string& str,
                     std::vector<std::string>* characters) {
  const char* data = str.data();
  const size_t length = str.size();
  for (size_t i = 0; i < length; /* no update */) {
    int c = data[i++] & 0xff;
    if ((c & 0x80) == 0) {
      characters->push_back(UTF8CodeToUTF8String(c));
    } else {
      if ((c & 0xc0) == 0x80) {
        LOG(ERROR) << "UTF8StringToLabels: continuation byte as lead byte";
        return false;
      }
      int count = (c >= 0xc0) + (c >= 0xe0) + (c >= 0xf0) + (c >= 0xf8) +
                  (c >= 0xfc);
      int code = c & ((1 << (6 - count)) - 1);
      while (count != 0) {
        if (i == length) {
          LOG(ERROR) << "UTF8StringToLabels: truncated utf-8 byte sequence";
          return false;
        }
        char cb = data[i++];
        if ((cb & 0xc0) != 0x80) {
          LOG(ERROR) << "UTF8StringToLabels: missing/invalid continuation byte";
          return false;
        }
        code = (code << 6) | (cb & 0x3f);
        count--;
      }
      if (code < 0) {
        // This should not be able to happen.
        LOG(ERROR) << "UTF8StringToLabels: Invalid character found: " << c;
        return false;
      }
      characters->push_back(UTF8CodeToUTF8String(code));
    }
  }
  return true;
}

}  // namespace asr_cc
