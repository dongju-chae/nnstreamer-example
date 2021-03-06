package org.nnsuite.nnstreamer.sample;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.nnsuite.nnstreamer.CustomFilter;
import org.nnsuite.nnstreamer.NNStreamer;
import org.nnsuite.nnstreamer.Pipeline;
import org.nnsuite.nnstreamer.SingleShot;
import org.nnsuite.nnstreamer.TensorsData;
import org.nnsuite.nnstreamer.TensorsInfo;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Sample code to run the application with nnstreamer-api.
 * Before building this sample, copy nnstreamer-api library file into 'libs' directory.
 */
public class MainActivity extends Activity {
    private static final String TAG = "NNStreamer-Sample";

    private static final int PERMISSION_REQUEST_CODE = 3;
    private static final String[] requiredPermissions = new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private boolean initialized = false;
    private boolean isFailed = false;
    private CountDownTimer exampleTimer = null;
    private int exampleRun = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        /* check permissions */
        for (String permission : requiredPermissions) {
            if (!checkPermission(permission)) {
                ActivityCompat.requestPermissions(this,
                        requiredPermissions, PERMISSION_REQUEST_CODE);
                return;
            }
        }

        initNNStreamer();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (initialized) {
            /* set timer to run examples */
            exampleRun = 0;
            isFailed = false;
            setExampleTimer(200);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        stopExampleTimer();
    }

    /**
     * Check the permission is granted.
     */
    private boolean checkPermission(final String permission) {
        return (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission denied, close app.");
                    finish();
                    return;
                }
            }

            initNNStreamer();
            return;
        }

        finish();
    }

    /**
     * Initialize NNStreamer.
     */
    private void initNNStreamer() {
        if (initialized) {
            return;
        }

        try {
            initialized = NNStreamer.initialize(this);
        } catch(Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        } finally {
            if (initialized) {
                Log.i(TAG, "Version: " + NNStreamer.getVersion());
            } else {
                Log.e(TAG, "Failed to initialize NNStreamer");
                finish();
            }
        }
    }

    /**
     * Set timer to run the examples.
     */
    private void setExampleTimer(long time) {
        stopExampleTimer();

        exampleTimer = new CountDownTimer(time, time) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                /* run the examples repeatedly */
                if (exampleRun > 5) {
                    Log.d(TAG, "Stop timer to run example");

                    if (isFailed) {
                        Log.d(TAG, "Error occurs while running the examples");
                    }

                    return;
                }

                int option = (exampleRun % 6);

                if (option == 1) {
                    Log.d(TAG, "==== Run pipeline example with state callback ====");
                    runPipe(true);
                } else if (option == 2) {
                    Log.d(TAG, "==== Run pipeline example ====");
                    runPipe(false);
                } else if (option == 3) {
                    Log.d(TAG, "==== Run pipeline example with valve ====");
                    runPipeValve();
                } else if (option == 4) {
                    Log.d(TAG, "==== Run pipeline example with switch ====");
                    runPipeSwitch();
                } else if (option == 5) {
                    Log.d(TAG, "==== Run pipeline example with custom filter ====");
                    runPipeCustomFilter();
                } else {
                    Log.d(TAG, "==== Run single-shot example ====");
                    runSingle();
                }

                exampleRun++;
                setExampleTimer(500);
            }
        };

        exampleTimer.start();
    }

    /**
     * Cancel example timer.
     */
    private void stopExampleTimer() {
        if (exampleTimer != null) {
            exampleTimer.cancel();
            exampleTimer = null;
        }
    }

    /**
     * Get the File object of image classification tf-lite model.
     */
    private File getExampleModel() {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        return new File(root + "/nnstreamer/tflite_model_img/mobilenet_v1_1.0_224_quant.tflite");
    }

    /**
     * Print tensors info.
     *
     * The data type of tensor in NNStreamer:
     * {@link NNStreamer#TENSOR_TYPE_INT32}
     * {@link NNStreamer#TENSOR_TYPE_UINT32}
     * {@link NNStreamer#TENSOR_TYPE_INT16}
     * {@link NNStreamer#TENSOR_TYPE_UINT16}
     * {@link NNStreamer#TENSOR_TYPE_INT8}
     * {@link NNStreamer#TENSOR_TYPE_UINT8}
     * {@link NNStreamer#TENSOR_TYPE_FLOAT64}
     * {@link NNStreamer#TENSOR_TYPE_FLOAT32}
     * {@link NNStreamer#TENSOR_TYPE_UNKNOWN}
     *
     * The maximum rank that NNStreamer supports.
     * {@link NNStreamer#TENSOR_RANK_LIMIT}
     *
     * The maximum number of tensor instances that tensors may have.
     * {@link NNStreamer#TENSOR_SIZE_LIMIT}
     */
    private void printTensorsInfo(TensorsInfo info) {
        int num = info.getTensorsCount();

        Log.d(TAG, "The number of tensors in info: " + num);
        for (int i = 0; i < num; i++) {
            int[] dim = info.getTensorDimension(i);

            Log.d(TAG, "Info index " + i +
                    " name: " + info.getTensorName(0) +
                    " type: " + info.getTensorType(0) +
                    " dim: " + dim[0] + ":" + dim[1] + ":" + dim[2] + ":" + dim[3]);
        }
    }

    /**
     * Print tensors data.
     *
     * The maximum number of tensor instances that tensors may have.
     * {@link NNStreamer#TENSOR_SIZE_LIMIT}
     */
    private void printTensorsData(TensorsData data) {
        int num = data.getTensorsCount();

        Log.d(TAG, "The number of tensors in data: " + num);
        for (int i = 0; i < num; i++) {
            ByteBuffer buffer = data.getTensorData(i);

            Log.d(TAG, "Data index " + i + " received " + buffer.capacity());
        }
    }

    /**
     * Example to run single-shot.
     */
    private void runSingle() {
        /* example with image classification tf-lite model */
        File model = getExampleModel();

        if (!model.exists()) {
            Log.w(TAG, "Cannot find the model file");
            return;
        }

        try {
            SingleShot single = new SingleShot(model);

            Log.d(TAG, "Get input tensors info");
            TensorsInfo inInfo = single.getInputInfo();
            printTensorsInfo(inInfo);

            Log.d(TAG, "Get output tensors info");
            TensorsInfo outInfo = single.getOutputInfo();
            printTensorsInfo(outInfo);

            /* set timeout (1 second) */
            single.setTimeout(1000);

            /* single-shot invoke */
            for (int i = 0; i < 15; i++) {
                /* dummy input */
                TensorsData in = TensorsData.allocate(inInfo);

                Log.d(TAG, "Try to invoke data " + (i + 1));

                TensorsData out = single.invoke(in);
                printTensorsData(out);

                Thread.sleep(50);
            }

            single.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            isFailed = true;
        }
    }

    /**
     * Example to run pipeline.
     *
     * The state of pipeline:
     * {@link NNStreamer#PIPELINE_STATE_UNKNOWN}
     * {@link NNStreamer#PIPELINE_STATE_NULL}
     * {@link NNStreamer#PIPELINE_STATE_READY}
     * {@link NNStreamer#PIPELINE_STATE_PAUSED}
     * {@link NNStreamer#PIPELINE_STATE_PLAYING}
     */
    private void runPipe(boolean addStateCb) {
        /* example with image classification tf-lite model */
        File model = getExampleModel();

        if (!model.exists()) {
            Log.w(TAG, "Cannot find the model file");
            return;
        }

        try {
            String desc = "appsrc name=srcx ! " +
                    "other/tensor,dimension=(string)3:224:224:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                    "tensor_filter framework=tensorflow-lite model=" + model.getAbsolutePath() + " ! " +
                    "tensor_sink name=sinkx";

            /* pipeline state callback */
            Pipeline.StateChangeCallback stateCb = null;

            if (addStateCb) {
                stateCb = new Pipeline.StateChangeCallback() {
                    @Override
                    public void onStateChanged(int state) {
                        Log.d(TAG, "The pipeline state changed to " + state);
                    }
                };
            }

            Pipeline pipe = new Pipeline(desc, stateCb);

            /* register sink callback */
            pipe.setSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                int received = 0;

                @Override
                public void onNewDataReceived(TensorsData data, TensorsInfo info) {
                    Log.d(TAG, "Received new data callback " + (++received));

                    printTensorsInfo(info);
                    printTensorsData(data);
                }
            });

            Log.d(TAG, "Current state is " + pipe.getState());

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 15; i++) {
                /* dummy input */
                TensorsData in = new TensorsData();
                in.addTensorData(TensorsData.allocateByteBuffer(3 * 224 * 224));

                Log.d(TAG, "Push input data " + (i + 1));

                pipe.inputData("srcx", in);
                Thread.sleep(50);
            }

            Log.d(TAG, "Current state is " + pipe.getState());

            pipe.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            isFailed = true;
        }
    }

    /**
     * Example to run pipeline with valve.
     */
    private void runPipeValve() {
        try {
            String desc = "appsrc name=srcx ! " +
                    "other/tensor,dimension=(string)3:100:100:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                    "tee name=t " +
                    "t. ! queue ! tensor_sink name=sink1 " +
                    "t. ! queue ! valve name=valvex ! tensor_sink name=sink2";

            Pipeline pipe = new Pipeline(desc);

            /* register sink callback */
            pipe.setSinkCallback("sink1", new Pipeline.NewDataCallback() {
                int received = 0;

                @Override
                public void onNewDataReceived(TensorsData data, TensorsInfo info) {
                    Log.d(TAG, "Received new data callback at sink1 " + (++received));

                    printTensorsInfo(info);
                    printTensorsData(data);
                }
            });

            pipe.setSinkCallback("sink2", new Pipeline.NewDataCallback() {
                int received = 0;

                @Override
                public void onNewDataReceived(TensorsData data, TensorsInfo info) {
                    Log.d(TAG, "Received new data callback at sink2 " + (++received));

                    printTensorsInfo(info);
                    printTensorsData(data);
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 15; i++) {
                /* dummy input */
                TensorsData in = new TensorsData();
                in.addTensorData(TensorsData.allocateByteBuffer(3 * 100 * 100));

                Log.d(TAG, "Push input data " + (i + 1));

                pipe.inputData("srcx", in);
                Thread.sleep(50);

                if (i == 10) {
                    /* close valve */
                    pipe.controlValve("valvex", false);
                }
            }

            pipe.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            isFailed = true;
        }
    }

    /**
     * Example to run pipeline with output-selector.
     */
    private void runPipeSwitch() {
        try {
            /* Note that the sink element needs option 'async=false'
             *
             * Prerolling problem
             * For running the pipeline, set async=false in the sink element when using an output selector.
             * The pipeline state can be changed to paused after all sink element receive buffer.
             */
            String desc = "appsrc name=srcx ! " +
                    "other/tensor,dimension=(string)3:100:100:1,type=(string)uint8,framerate=(fraction)0/1 ! " +
                    "output-selector name=outs " +
                    "outs.src_0 ! tensor_sink name=sink1 async=false " +
                    "outs.src_1 ! tensor_sink name=sink2 async=false";

            Pipeline pipe = new Pipeline(desc);

            /* register sink callback */
            pipe.setSinkCallback("sink1", new Pipeline.NewDataCallback() {
                int received = 0;

                @Override
                public void onNewDataReceived(TensorsData data, TensorsInfo info) {
                    Log.d(TAG, "Received new data callback at sink1 " + (++received));

                    printTensorsInfo(info);
                    printTensorsData(data);
                }
            });

            pipe.setSinkCallback("sink2", new Pipeline.NewDataCallback() {
                int received = 0;

                @Override
                public void onNewDataReceived(TensorsData data, TensorsInfo info) {
                    Log.d(TAG, "Received new data callback at sink2 " + (++received));

                    printTensorsInfo(info);
                    printTensorsData(data);
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 15; i++) {
                /* dummy input */
                TensorsData in = new TensorsData();
                in.addTensorData(TensorsData.allocateByteBuffer(3 * 100 * 100));

                Log.d(TAG, "Push input data " + (i + 1));

                pipe.inputData("srcx", in);
                Thread.sleep(50);

                if (i == 10) {
                    /* select pad */
                    pipe.selectSwitchPad("outs", "src_1");
                }
            }

            /* get pad list of output-selector */
            String[] pads = pipe.getSwitchPads("outs");
            Log.d(TAG, "Total pad in output-selector: " + pads.length);
            for (String pad : pads) {
                Log.d(TAG, "Pad name: " + pad);
            }

            pipe.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            isFailed = true;
        }
    }

    /**
     * Example to run pipeline with custom filter.
     */
    private void runPipeCustomFilter() {
        try {
            /* register custom-filter (passthrough) */
            CustomFilter customPassthrough = CustomFilter.registerCustomFilter("custom-passthrough",
                    new CustomFilter.CustomFilterCallback() {
                @Override
                public TensorsInfo getOutputInfo(TensorsInfo inInfo) {
                    Log.d(TAG, "Received info callback in custom-passthrough");
                    return inInfo;
                }

                @Override
                public TensorsData invoke(TensorsData inData, TensorsInfo inInfo, TensorsInfo outInfo) {
                    Log.d(TAG, "Received invoke callback in custom-passthrough");
                    return inData;
                }
            });

            /* register custom-filter (convert data type to float) */
            CustomFilter customConvert = CustomFilter.registerCustomFilter("custom-convert",
                    new CustomFilter.CustomFilterCallback() {
                @Override
                public TensorsInfo getOutputInfo(TensorsInfo inInfo) {
                    Log.d(TAG, "Received info callback in custom-convert");

                    TensorsInfo out = inInfo;
                    out.setTensorType(0, NNStreamer.TENSOR_TYPE_FLOAT32);

                    return out;
                }

                @Override
                public TensorsData invoke(TensorsData inData, TensorsInfo inInfo, TensorsInfo outInfo) {
                    Log.d(TAG, "Received invoke callback in custom-convert");

                    ByteBuffer input = inData.getTensorData(0);
                    ByteBuffer output = TensorsData.allocateByteBuffer(4 * 10);

                    for (int i = 0; i < 10; i++) {
                        float value = (float) input.getInt(i * 4);
                        output.putFloat(i * 4, value);
                    }

                    TensorsData out = new TensorsData();
                    out.addTensorData(output);

                    return out;
                }
            });

            /* register custom-filter (add constant) */
            CustomFilter customAdd = CustomFilter.registerCustomFilter("custom-add",
                    new CustomFilter.CustomFilterCallback() {
                @Override
                public TensorsInfo getOutputInfo(TensorsInfo inInfo) {
                    Log.d(TAG, "Received info callback in custom-add");
                    return inInfo;
                }

                @Override
                public TensorsData invoke(TensorsData inData, TensorsInfo inInfo, TensorsInfo outInfo) {
                    Log.d(TAG, "Received invoke callback in custom-add");

                    ByteBuffer input = inData.getTensorData(0);
                    ByteBuffer output = TensorsData.allocateByteBuffer(4 * 10);

                    for (int i = 0; i < 10; i++) {
                        float value = input.getFloat(i * 4);

                        /* add constant */
                        value += 1.5;
                        output.putFloat(i * 4, value);
                    }

                    TensorsData out = new TensorsData();
                    out.addTensorData(output);

                    return out;
                }
            });

            String desc = "appsrc name=srcx ! " +
                    "other/tensor,dimension=(string)10:1:1:1,type=(string)int32,framerate=(fraction)0/1 ! " +
                    "tensor_filter framework=" + customPassthrough.getName() + " ! " +
                    "tensor_filter framework=" + customConvert.getName() + " ! " +
                    "tensor_filter framework=" + customAdd.getName() + " ! " +
                    "tensor_sink name=sinkx";

            Pipeline pipe = new Pipeline(desc);

            /* register sink callback */
            pipe.setSinkCallback("sinkx", new Pipeline.NewDataCallback() {
                int received = 0;

                @Override
                public void onNewDataReceived(TensorsData data, TensorsInfo info) {
                    Log.d(TAG, "Received new data callback at sinkx " + (++received));

                    printTensorsInfo(info);
                    printTensorsData(data);

                    ByteBuffer output = data.getTensorData(0);

                    for (int i = 0; i < 10; i++) {
                        Log.d(TAG, "Received data: index " + i + " value " + output.getFloat(i * 4));
                    }
                }
            });

            /* start pipeline */
            pipe.start();

            /* push input buffer */
            for (int i = 0; i < 15; i++) {
                ByteBuffer input = TensorsData.allocateByteBuffer(4 * 10);

                for (int j = 0; j < 10; j++) {
                    input.putInt(j * 4, j);
                }

                TensorsData in = new TensorsData();
                in.addTensorData(input);

                pipe.inputData("srcx", in);
                Thread.sleep(50);
            }

            pipe.close();

            /* close custom-filter */
            customPassthrough.close();
            customConvert.close();
            customAdd.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            isFailed = true;
        }
    }
}
