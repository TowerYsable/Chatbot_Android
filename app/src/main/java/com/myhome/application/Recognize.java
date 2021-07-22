package com.myhome.application;

/*使用C++库，用于流式ASR
init() 初始化model: modelPath:模型路径   dictPath:词典路径
reset()  重启ASR
acceptWaveform 接受buffer数据流
setInputFinised  获取voice结束，主要是非流式的标记
getFinied()  true false,是否结束标记
startDecode()  解码
getResult()  返回结果
* */

public class Recognize {

  static {
    System.loadLibrary("asr_cc");
  }

  public static native void init(String modelPath, String dictPath);
  public static native void reset();
  public static native void acceptWaveform(short[] waveform);
  public static native void setInputFinished();
  public static native boolean getFinished();
  public static native void startDecode();
  public static native String getResult();
}
