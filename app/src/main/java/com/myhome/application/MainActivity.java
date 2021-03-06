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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import ai.kitt.snowboy.SnowboyDetect;
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
  // ASR ??????
  private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
  private static final String LOG_TAG = "voice";  // ??????voice??????
  private static final String Finished_TAG = "Finished";  // ???????????????????????????
  private static final int SAMPLE_RATE = 16000;//16khz
  private static final int MAX_QUEUE_SIZE = 2500;  // 100 seconds audio, 1 / 0.04 * 100
  private static final int MAX_AUDIO_DURATION_MS = 5000;   //??????????????????10s
//  private int MAX_AUDIO_DURATION_MS;   //??????????????????10s
  private boolean startRecord = false;  //??????????????????
  private AudioRecord record = null;
  private int miniBufferSize = 0;  // 1280 bytes 648 byte 40ms, 0.04s
  private final BlockingQueue<short[]> bufferQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
  private int timeMs = 0;
  // Bot ??????
  private EditText editText ; //??????
  private final int USER = 0; // user=0 ,user?????????bot??????
  private final int BOT = 1; //bot=0???bot?????????user??????
  private UserMessage userMessage; //????????????
  // TTS??????
  private SpeechSynthesizer speechSynthesizer;
  private String bot_message;    //??????http????????????
  private static final String TAG = "TTS";   //??????log??????
  MediaPlayer mediaPlayer;  //mediaPlayer
  // Snowboy??????
  private boolean shouldDetect;  // bool????????????snowboy????????????
  boolean Detected = false; // ???????????????
  private SnowboyDetect snowboyDetect;
  private MediaPlayer player = new MediaPlayer();  // ????????????"ding.wav"
  static {
    System.loadLibrary("snowboy-detect-android");  // ??????snowboy??????JNI
  }
  // VAD??????
  private VoiceRecorder recorder;
  private VadConfig config;
  private boolean isRecording = false;
  private boolean isWork  = false;
  private long speechtime;
  private long noisetime;
  private int flag = 0;
  private boolean isVad = true;

  //  #########################################
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // TODO:VAD
    config = VadConfig.newBuilder()
            .setSampleRate(VadConfig.SampleRate.SAMPLE_RATE_16K)
            .setFrameSize(VadConfig.FrameSize.FRAME_SIZE_160)
            .setMode(VadConfig.Mode.VERY_AGGRESSIVE)
            .setSilenceDurationMillis(500)
            .setVoiceDurationMillis(500)
            .build();
    recorder = new VoiceRecorder(this, config);
    //  TODO: TTS
    SpeechUtility.createUtility(this, "appid=5f843556");  // ???????????? APPID
    speechSynthesizer = SpeechSynthesizer.createSynthesizer(MainActivity.this, null); //?????????
    speechSynthesizer.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan"); //???????????????
    speechSynthesizer.setParameter(SpeechConstant.SPEED, "50"); //????????????
    speechSynthesizer.setParameter(SpeechConstant.VOLUME, "100"); //?????????????????????0~100
    Setting.setShowLog(false);
    //  TODO??? BOT --> chat chatScrollView:chatbot????????????edittext:??????????????? ; sendBtn:??????????????????????????????
    final NestedScrollView chatScrollView = this.findViewById(R.id.chatScrollView);
    editText = findViewById(R.id.edittext_chatbox);
    chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    FloatingActionButton sendBtn = findViewById(R.id.send_button);
    sendBtn.setOnClickListener(it -> MainActivity.this.sendMessage());
    //  TODO??? ASR
    requestAudioPermissions();//  ??????????????????
    final String modelPath = new File(calculateDb.assetFilePath(this, "final.zip")).getAbsolutePath();
    final String dictPath = new File(calculateDb.assetFilePath(this, "words.txt")).getAbsolutePath();
    TextView textView = findViewById(R.id.textView);
    textView.setText("");
    Recognize.init(modelPath, dictPath); //notice: ????????? ASR model
    // TODO????????????????????????ASR  ?????????????????????
    Button button = findViewById(R.id.button);  // ??????button
    button.setText("????????????/??????snowboy");
    button.setOnClickListener(view -> {
      if (!startRecord) {
        startRecord = true;
        timeMs = 0;
        speechSynthesizer.pauseSpeaking();  //stop tts
//        audioRecord.stop(); --> ????????????
//        audioRecord.release();
        Recognize.reset();
        startRecordThread();
        startAsrThread();
        Recognize.startDecode();
        button.setText("????????????");
        button.setEnabled(false);
      } else {
        startRecord = false;
        Recognize.setInputFinished();
        button.setText("????????????/??????snowboy");
        button.setEnabled(false);
      }
    });
    //   TODO??? ai.kitt.snowboy ??????
    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
    } else {
      initMediaPalyer(this);//?????????????????? MediaPlayer
    }
    setupHotword();

    startTts("???????????????????????????????????????????????????");
  }
  private void startAsrIng(){
    if (!startRecord) {
      shouldDetect = false;
      startRecord = true;
      timeMs = 0;
      speechSynthesizer.pauseSpeaking();  //stop tts
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

  // TODO: vad,??????vad?????????vad
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

  //  #########################################
//  TODO:?????????????????????????????????,??????slam
  public interface Slam_api {
    @GET("/api/stuta")
    Call<String> text(@Query("text") String text);
  }
// TODO???BOT
  private void sendMessage() {
    String msg = editText.getText().toString();
    OkHttpClient okHttpClient = new OkHttpClient();
    Retrofit retrofit = new Retrofit.Builder()
//            .baseUrl("http://10.21.63.65:5005/webhooks/rest/").client(okHttpClient)
            .baseUrl("http://192.168.1.51:5005/webhooks/rest/").client(okHttpClient)
//            .baseUrl("http://10.1.1.137:5005/webhooks/rest/").client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()).build();
    if (msg.trim().isEmpty()) {
      Toast.makeText(MainActivity.this, "Please enter your query!", Toast.LENGTH_LONG).show();
    } else {
      showTextView(msg, USER);
//      startTts(msg);  //??????TTS
      editText.setText("");
      userMessage = new UserMessage("User", msg);
    }
    MessageSender messageSender = retrofit.create(MessageSender.class);
    Call<List<BotResponse>> response = messageSender.sendMessage(userMessage);
    response.enqueue(new Callback<List<BotResponse>>() {
      @Override
      public void onResponse(Call<List<BotResponse>> call, Response<List<BotResponse>> response) {
        if(response.body() == null || response.body().size() == 0){
          showTextView("???????????????????????????????????????",BOT);
          startTts("???????????????????????????????????????");
        }
        else{
          BotResponse botResponse = response.body().get(0);
          showTextView(botResponse.getText(),BOT);
          bot_message = botResponse.getText();
          startTts(bot_message);
//          TODO???doing slam
          Retrofit retrofit_slam = new Retrofit.Builder()
                    .baseUrl("http://192.168.1.51:1998")
//                    .baseUrl("http://10.1.1.137:1998")
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
          Slam_api api = retrofit_slam.create(Slam_api.class);
          //?????????????????????????????? ??????????????????????????????
          Call<String> slam_response = api.text(bot_message);
          slam_response.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
              Log.d("slam succuss","succuss");
              //????????????????????????
            }
            @Override
            public void onFailure(Call<String> call, Throwable t) {
              Log.d("slam bad","bad");
            }
          });

          if(botResponse.buttons != null){
            Log.e("Button c", "${botResponse.buttons.size}");
          }
        }
      }
      @Override
      public void onFailure(Call<List<BotResponse>> call, Throwable t) {
        showTextView("?????????chatbot????????????",BOT); //network??????
        startTts("?????????chatbot????????????");
      }
    });
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

    layout.requestFocus();
    editText.requestFocus();
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
//  TODO???init ASR
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
//    File model = new File(snowboyDirectory, "xiaobaoxiaobao.pmdl");
    File model = new File(snowboyDirectory, "mic.pdml");
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
      AudioRecord audioRecord = new AudioRecord(
              MediaRecorder.AudioSource.DEFAULT,
              16000,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
              bufferSize
      );

      if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
        Log.e("hotword", "audio record fail to initialize");
        return;
      }

      audioRecord.startRecording();
      Log.d("hotword", "start listening to hotword");

      while (shouldDetect) {
        audioRecord.read(audioBuffer, 0, audioBuffer.length);

        short[] shortArray = new short[audioBuffer.length / 2];
        ByteBuffer.wrap(audioBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray);

        int result = snowboyDetect.runDetection(shortArray, shortArray.length);
        if (result > 0) {
          Log.d("hotword", "detected");
          shouldDetect = false;
          Detected = true;
        }
      }

      audioRecord.stop();
      audioRecord.release();
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

//  ##################################3
// TODO:TTS  ??????TTS??????client  -> xunfei
//  @SuppressLint("LongLogTag")
//  public void  onTts(String message)
//  {
//      onDestory(mediaPlayer);
//      speechSynthesizer.startSpeaking(message, mListener);
//  }
  public void startTts(String message){
    //TODO: Start TTS
    onDestory(mediaPlayer);
    speechSynthesizer.startSpeaking(message,mListener);
    // TODO: ??????????????????
    Runnable runnable3 = new Runnable() {
      @Override
      public void run() {
        while (speechSynthesizer.isSpeaking() && !Recognize.getFinished()){
          try {
            Thread.sleep(100);
          } catch (InterruptedException e){
            Log.e("tts",e.getMessage(),e);
          }
        }
        // TODO????????????

        startHotword();
//
//        // TODO:?????????
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

      }
    };
    Threadings.runInBackgroundThread(runnable3);
  }

//  TODO:TTS
  private final SynthesizerListener mListener = new SynthesizerListener(){
    @Override
    public void onBufferProgress(int arg0, int arg1, int arg2, String arg3) {
      // TODO Auto-generated method stub
    }
    @SuppressLint("LongLogTag")
    @Override
    public void onCompleted(SpeechError error) {
      // TODO Auto-generated method stub
      if(error!=null)
      {
        Log.d("mySynthesiezer complete code:", error.getErrorCode()+"");
      }
      else
      {
        Log.d("mySynthesiezer complete code:", "0");
      }
      Toast.makeText(MainActivity.this, "????????????", Toast.LENGTH_LONG).show();
    }
    @Override
    public void onEvent(int arg0, int arg1, int arg2, Bundle arg3) {
      // TODO Auto-generated method stub
    }
    @Override
    public void onSpeakBegin() {
      // TODO Auto-generated method stub
    }
    @Override
    public void onSpeakPaused() {
      // TODO Auto-generated method stub
    }
    @Override
    public void onSpeakProgress(int arg0, int arg1, int arg2) {
      // TODO Auto-generated method stub
    }
    @Override
    public void onSpeakResumed() {
      // TODO Auto-generated method stub
    }
  };

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

//        if (speechtime>500){  //??????1s
//          MAX_AUDIO_DURATION_MS = (int)speechtime;
//        }else {MAX_AUDIO_DURATION_MS = 5000;}  //TODO:?????????
//        Log.d("MAX_AUDIO_DURATION_MS", String.valueOf(MAX_AUDIO_DURATION_MS));
        if (timeMs >= MAX_AUDIO_DURATION_MS) {
          startRecord = false;
          Recognize.setInputFinished();
          runOnUiThread(() -> {
            Toast.makeText(MainActivity.this,
                    String.format("?????? %d seconds???????????????", MAX_AUDIO_DURATION_MS / 1000),
                    Toast.LENGTH_LONG).show();
            button.setText("????????????");
            button.setEnabled(false);
          });
        }
      }
      record.stop();
//      record.release();
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
            // ???????????????????????? sendMessage???????????????????????????
            if (text_test.length() != 0){
              sendMessage();
            }
            else if(!Detected){ //TODO????????????????????????
              try {
                Thread.sleep(500);
              }catch (InterruptedException e){
                Log.e("asr to wakeup",e.getMessage(),e);
              }
                startHotword();
            }
          });
          break;
        }
      }
    }).start();
  }

  public void onDestory(MediaPlayer mediaplayer) {
    if (mediaplayer != null) {
      if (mediaplayer.isPlaying()) {
        mediaplayer.stop();
      }
      mediaplayer.release();
    }
  }

}