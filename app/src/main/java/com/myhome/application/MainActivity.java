package com.myhome.application;

import android.Manifest;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.Setting;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;
import com.konovalov.vad.Vad;
import com.konovalov.vad.VadConfig;
import com.konovalov.vad.VadListener;
import com.myhome.application.snowboydemo.SnowboyUtils;
import com.myhome.application.snowboydemo.Threadings;
import com.myhome.application.tools.calculateDb;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import ai.kitt.snowboy.SnowboyDetect;
import jackmego.com.jieba_android.JiebaSegmenter;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;


public class MainActivity extends AppCompatActivity implements VoiceRecorder.Listener {
  // ASR 参数
  private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
  private static final String LOG_TAG = "voice";  // 提示voice信息
  private static final String Finished_TAG = "Finished";  // 结束标志，用于测试
  private static final int SAMPLE_RATE = 16000;//16khz
  private static final int MAX_QUEUE_SIZE = 2500;  // 100 seconds audio, 1 / 0.04 * 100
  private static final int MAX_AUDIO_DURATION_MS = 5000;   //最长等待时间10s
//  private int MAX_AUDIO_DURATION_MS;   //最长等待时间10s
  private boolean startRecord = false;  //是否开始录音
  private AudioRecord record = null;
  private int miniBufferSize = 0;  // 1280 bytes 648 byte 40ms, 0.04s
  private final BlockingQueue<short[]> bufferQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
  private int timeMs = 0;
  // Bot 参数
  private EditText editText ; //输入
  private final int USER = 0; // user=0 ,user输入，bot等待
  private final int BOT = 1; //bot=0，bot等待，user输入
  private UserMessage userMessage; //用户信息
  // TTS参数
  private SpeechSynthesizer speechSynthesizer;
  private String bot_message;    //接收http音频数据
  private static final String TAG = "TTS";   //用于log测试
  MediaPlayer mediaPlayer;  //mediaPlayer
  // Snowboy参数
  private boolean shouldDetect;  // bool是否使用snowboy进行检测
  boolean Detected = false; // 是否检测到
  private SnowboyDetect snowboyDetect;
  private MediaPlayer player = new MediaPlayer();  // 用于播放"ding.wav" 和
  static {
    System.loadLibrary("snowboy-detect-android");  // 加载snowboy——JNI
  }
  private int Hotflag = 0;
  // VAD参数
  private VoiceRecorder recorder;
  private VadConfig config;
  private boolean isRecording = false;
  private boolean isWork  = false;
  private long speechtime;
  private long noisetime;
  private int flag = 0;
  private boolean isVad = true;
  private String bot_msg;
  // jieba分词
  // wav
//  private MediaPlayer playerWav = new MediaPlayer();  // 用于播放"ding.wav" 和
  //  #########################################
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // TODO:jieba
    JiebaSegmenter.init(getApplicationContext());
    // TODO:VAD
    config = VadConfig.newBuilder()
            .setSampleRate(VadConfig.SampleRate.SAMPLE_RATE_16K)
            .setFrameSize(VadConfig.FrameSize.FRAME_SIZE_160)
            .setMode(VadConfig.Mode.VERY_AGGRESSIVE)
            .setSilenceDurationMillis(500)
            .setVoiceDurationMillis(500)
            .build();
    recorder = new VoiceRecorder(this, config);

    //  TODO： BOT --> chat chatScrollView:chatbot聊天窗；edittext:文本输入框 ; sendBtn:点击发送输入框的信息
    final NestedScrollView chatScrollView = this.findViewById(R.id.chatScrollView);
    editText = findViewById(R.id.edittext_chatbox);
    chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    FloatingActionButton sendBtn = findViewById(R.id.send_button);

//    sendBtn.setOnClickListener(it -> MainActivity.this.sendMessage());
    sendBtn.setOnClickListener(it -> MainActivity.this.sendMessage2());
    //  TODO： ASR
    requestAudioPermissions();//  获取录音权限
    final String modelPath = new File(calculateDb.assetFilePath(this, "final.zip")).getAbsolutePath();
    final String dictPath = new File(calculateDb.assetFilePath(this, "words.txt")).getAbsolutePath();
    TextView textView = findViewById(R.id.textView);
    textView.setText("");
    Recognize.init(modelPath, dictPath); //notice: 初始化 ASR model
    // TODO：点击的方式进行ASR  这里已经不使用
    Button button = findViewById(R.id.button);  // 录音button
    button.setText("开始录音/结束snowboy");
    button.setOnClickListener(view -> {
      if (!startRecord) {
        startRecord = true;
        timeMs = 0;
        timeMs = 0;
//        player.stop();
//        speechSynthesizer.pauseSpeaking();  //stop tts
//        audioRecord.stop(); --> 等待完成
//        audioRecord.release();
        Recognize.reset();
        startRecordThread();
        startAsrThread();
        Recognize.startDecode();
        button.setText("结束录音");
        button.setEnabled(false);
      } else {
        startRecord = false;
        Recognize.setInputFinished();
        button.setText("开始录音/结束snowboy");
        button.setEnabled(false);
      }
    });
    //   TODO： ai.kitt.snowboy 权限
    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
    } else {
      initMediaPalyer(this);//初始化播放器 MediaPlayer
    }
    setupHotword();

    File snowboyDirectory = SnowboyUtils.getSnowboyDirectory(this);
    MediaPlayer playerWav = new MediaPlayer();
    try {
      playerWav.setDataSource(snowboyDirectory+"/" +"start.wav");
      playerWav.prepare();
    } catch (IOException e) {
      e.printStackTrace();
      Log.e(TAG, "Playing tts error", e);
    }
//    initMediaPalyerTts(this,"/6.wav");
    playerWav.start();
    while (playerWav.isPlaying() ){
      try {
        Thread.sleep(100);
      } catch (InterruptedException e){
        Log.e("tts",e.getMessage(),e);
      }
    }
    playerWav.stop();
    playerWav.release();
    startHotword();

//    startTts("我是您的贴心小助手，您可以叫我小宝");
  }
  private void startAsrIng(){
//    player.stop(); 这样可能会直接跳过ding
    if (!startRecord) {
      shouldDetect = false;
      startRecord = true;
      timeMs = 0;
//      player.stop();
//      speechSynthesizer.pauseSpeaking();  //stop tts
      Recognize.reset();
      startRecordThread();
      startAsrThread();
      Recognize.startDecode();
    } else {
      shouldDetect = true;
      startRecord = false;
      Recognize.setInputFinished();
    }
  }
//  ########################################################################

  // TODO: vad,开启vad和关闭vad
  private void startRecordingVad() {
    isRecording = true;
    recorder.start();
  }
  private void stopRecordingVad() {
    isRecording = false;
    recorder.stop();
  }

  @Override
  public void onSpeechDetected() {
    Runnable runnable1 = ()->{
      speechtime = VoiceRecorder.ReturnSpeechTime();
      Log.e("speechtime_2", String.valueOf(speechtime));
      if (speechtime > 5000){
        stopRecordingVad();
        flag = 1;
        Log.e("speech_flag", String.valueOf(flag));
      }
    };
    Threadings.runInBackgroundThread(runnable1);
  }
    @Override
    public void onNoiseDetected() {
      Runnable runnable2 = ()->{
        noisetime = VoiceRecorder.ReturnNoiseTime();
        Log.e("noise111111111111", String.valueOf(noisetime));
        if (noisetime > 10000){
          stopRecordingVad();
          flag = 2;
          Log.e("noise_flag", String.valueOf(flag));
        }
      };
      Threadings.runInBackgroundThread(runnable2);
    }

//send
  private void sendMessage2(){
    String msg = editText.getText().toString();
    if (msg.trim().isEmpty()) {
      Toast.makeText(MainActivity.this, "请输入你的请求!", Toast.LENGTH_LONG).show();
    } else {
      showTextView(msg, USER);
      editText.setText("");
      userMessage = new UserMessage("User", msg);
    }
    ArrayList<String> wordlist = JiebaSegmenter.getJiebaSegmenterSingleton().getDividedString(msg);
    for (Iterator text_msg = wordlist.iterator(); text_msg.hasNext();) {
      String word = (String) text_msg.next();
      if (word.equals("今天天气")){
        bot_msg = "今天天气良好";
        Hotflag = 1;
      }else if (word.equals("你好") || word.equals("介绍")){
        bot_msg = "我是您的贴心小助手小宝，请问有什么可以帮到您的吗？";
        Hotflag = 2;
      }else if (word.equals("早餐")){
        bot_msg = "早餐在二楼餐厅";
        Hotflag = 3;
      }else if (word.equals("健身房")){
        bot_msg = "健身在酒店的顶层。";
        Hotflag = 4;
      }else if (word.equals("周边")){
        bot_msg = "周边有许多好玩的，这就给你介绍";
        Hotflag = 5;
      }else if (word.equals("带路")){
        bot_msg = "让我带你去吧";
        Hotflag = 6;
      }else if(word.equals("退下")){
        bot_msg = "好的";
        Hotflag = 7;
      }
    }
    showTextView(bot_msg,BOT);
    File snowboyDirectory = SnowboyUtils.getSnowboyDirectory(this);
    MediaPlayer playerWav = new MediaPlayer();
    try {
      playerWav.setDataSource(snowboyDirectory+"/" + Hotflag+".wav");
      playerWav.prepare();
    } catch (IOException e) {
      e.printStackTrace();
      Log.e(TAG, "Playing tts error", e);
    }
//    initMediaPalyerTts(this,"/6.wav");
    playerWav.start();
    while(playerWav.isPlaying()){
      try {
        Thread.sleep(100);
      } catch (InterruptedException e){
        Log.e("tts",e.getMessage(),e);
      }
    }
    playerWav.stop();
    playerWav.release();

    if (Hotflag == 7){
      startHotword();
    }else{
      startAsrIng();
    }
    Hotflag = 0;
  }


  private void showTextView(String message, int type) {
    LinearLayout chatLayout = findViewById(R.id.chat_layout);
    LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
    FrameLayout layout;
    if (type == USER) {
      layout = getUserLayout();
    } else {
      layout = getBotLayout();
    }
    layout.setFocusableInTouchMode(true);
    chatLayout.addView(layout);
    TextView tv = layout.findViewById(R.id.chat_msg);
    tv.setText(message);
//    layout.requestFocus();
//    editText.requestFocus();

    Date date = new Date(System.currentTimeMillis());
    SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm aa",
            Locale.ENGLISH);
    String time = dateFormat.format(date);
    TextView timeTextView = layout.findViewById(R.id.message_time);
    timeTextView.setText(time.toString());
  }

  FrameLayout getUserLayout() {
    LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
    return (FrameLayout) inflater.inflate(R.layout.user_message_box, null);
  }

  FrameLayout getBotLayout() {
    LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
    return (FrameLayout) inflater.inflate(R.layout.bot_message_box, null);
  }
  //  ########################################
//  TODO：init ASR
  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String permissions[], int[] grantResults) {
    if (requestCode == MY_PERMISSIONS_RECORD_AUDIO) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Log.i(LOG_TAG, "record permission is granted");
        initRecoder();
      } else {
        Toast.makeText(this, "Permissions denied to record audio", Toast.LENGTH_LONG).show();
        Button button = findViewById(R.id.button);
        button.setEnabled(false);
      }
    }
  }

//TODO: ai.kitt.snowboy
  private void setupHotword() {
    shouldDetect = false;
    SnowboyUtils.copyAssets(this);

    // TODO: Setup Model File
    File snowboyDirectory = SnowboyUtils.getSnowboyDirectory(this);
    File model = new File(snowboyDirectory, "xiaobaoxiaobao.pmdl");
//    File model = new File(snowboyDirectory, "mic.pdml");
//    File model = new File(snowboyDirectory, "mulmic.pmdl"); // 0.47
    File common = new File(snowboyDirectory, "common.res");

    // TODO: Set Sensitivity
    snowboyDetect = new SnowboyDetect(common.getAbsolutePath(), model.getAbsolutePath());
    snowboyDetect.setSensitivity("0.52"); // huawei 0.48
    snowboyDetect.applyFrontend(true);
  }

  private void startHotword() {
    Runnable runnable = () -> {
      shouldDetect = true;
      Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
      int bufferSize = 3200;
      byte[] audioBuffer = new byte[bufferSize];
      AudioRecord audioRecordHot = new AudioRecord(
              MediaRecorder.AudioSource.DEFAULT,
              16000,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
              bufferSize
      );

      if (audioRecordHot.getState() != AudioRecord.STATE_INITIALIZED) {
        Log.e("hotword", "audio record fail to initialize");
        return;
      }

      audioRecordHot.startRecording();
      Log.d("hotword", "start listening to hotword");

      while (shouldDetect) {
        audioRecordHot.read(audioBuffer, 0, audioBuffer.length);

        short[] shortArray = new short[audioBuffer.length / 2];
        ByteBuffer.wrap(audioBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray);

        int result = snowboyDetect.runDetection(shortArray, shortArray.length);
        if (result > 0) {
          Log.d("hotword", "detected");
          shouldDetect = false;
          Detected = true;
        }
      }

      audioRecordHot.stop();
      audioRecordHot.release();
      player.start();

      Log.d("hotword", "stop listening to hotword");
      if (Detected) {
        startAsrIng();
        Detected = false;
      }

    };
    Threadings.runInBackgroundThread(runnable);
  }

//  TODO: player ding.wav
  private void initMediaPalyer(Context context){
    try {
      File snowboyDirectory = SnowboyUtils.getSnowboyDirectory(context);
      player.setDataSource(snowboyDirectory+"/ding.wav");
      player.prepare();
    } catch (IOException e) {
      e.printStackTrace();
      Log.e(TAG, "Playing ding sound error", e);
    }
  }


//        // TODO:多轮的
//        startRecordingVad();
//        if (flag == 0){
//          startHotword();
//        }
//        if (flag == 2 || flag ==0){
//          if (noisetime > 10000){
//            stopRecordingVad();
//          }
//          Log.d("flab","this in here");
//          startHotword();
//        }else if(flag == 1){
////          if (speechtime > 5000){
////            stopRecordingVad();
////          }
//            startAsrIng();
//            Log.d("flab2","this in here");
//        }

//  TODO:TTS


  private void requestAudioPermissions() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
              new String[]{Manifest.permission.RECORD_AUDIO},
              MY_PERMISSIONS_RECORD_AUDIO);
    } else {
      initRecoder();
    }
  }

  private void initRecoder() {
    // buffer size in bytes 1280
    miniBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    if (miniBufferSize == AudioRecord.ERROR || miniBufferSize == AudioRecord.ERROR_BAD_VALUE) {
      Log.e(LOG_TAG, "Audio buffer can't initialize!");
      return;
    }
    record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            miniBufferSize);
    if (record.getState() != AudioRecord.STATE_INITIALIZED) {
      Log.e(LOG_TAG, "Audio Record can't initialize!");
      return;
    }
    Log.i(LOG_TAG, "Record init okay");
  }

  private void startRecordThread() {
    new Thread(() -> {
      VoiceRectView voiceView = findViewById(R.id.voiceRectView);
      record.startRecording();
      Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
      while (startRecord) {
        short[] buffer = new short[miniBufferSize / 2];
        int read = record.read(buffer, 0, buffer.length);
        voiceView.add(calculateDb.calculateDb2(buffer));
        try {
          if (AudioRecord.ERROR_INVALID_OPERATION != read) {
            bufferQueue.put(buffer);
          }
        } catch (InterruptedException e) {
          Log.e(LOG_TAG, e.getMessage());
        }
        timeMs += read * 1000 / SAMPLE_RATE;
        Button button = findViewById(R.id.button);
        if (timeMs >= 200 && !button.isEnabled() && startRecord) {
          runOnUiThread(() -> button.setEnabled(true));
        }

        if (timeMs >= MAX_AUDIO_DURATION_MS) {
          startRecord = false;
          Recognize.setInputFinished();
          runOnUiThread(() -> {
            Toast.makeText(MainActivity.this,
                    String.format("超过 %d seconds，录音结束", MAX_AUDIO_DURATION_MS / 1000),
                    Toast.LENGTH_LONG).show();
            button.setText("开始录音");
            button.setEnabled(false);
          });
        }
      }
      record.stop();
      voiceView.zero();
    }).start();
  }

// TODO: ASR_Thread
  private void startAsrThread() {
    new Thread(() -> {
      while (startRecord || bufferQueue.size() > 0) {
        try {
          short[] data = bufferQueue.take();
          Recognize.acceptWaveform(data);
        } catch (InterruptedException e) {
          Log.e(LOG_TAG, e.getMessage());
        }
      }
      while (true) {
        // get result
        if (!Recognize.getFinished()) {
          runOnUiThread(() -> {
//            Log.d("test", "startAsrThread: ");
          });
        } else {
          runOnUiThread(() -> {
            Button button = findViewById(R.id.button);
            Log.d(Finished_TAG, "startAsrThread2: ");
            button.setEnabled(true);
            EditText editText = findViewById(R.id.edittext_chatbox);
            editText.setText(Recognize.getResult());
            String text_test = editText.getText().toString();
            // 空的时候就不执行 sendMessage，而是进行唤醒监听
            if (text_test.length() != 0){
              sendMessage2();
//              sendMessage();
            }
            else if(!Detected){ //TODO：得加上静音检测
              File snowboyDirectory = SnowboyUtils.getSnowboyDirectory(this);
              MediaPlayer playerWav = new MediaPlayer();
              try {
                playerWav.setDataSource(snowboyDirectory+"/" +"else_vad.wav");
                playerWav.prepare();
              } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Playing tts error", e);
              }
              playerWav.start();

              while (playerWav.isPlaying() ){
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e){
                  Log.e("tts",e.getMessage(),e);
                }
              }
              playerWav.stop();
              playerWav.release();
              startHotword();
            }
          });
          break;
        }
      }
    }).start();
  }


}