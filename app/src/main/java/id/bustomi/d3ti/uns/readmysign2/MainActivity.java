package id.bustomi.d3ti.uns.readmysign2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;

import id.bustomi.d3ti_uns.readmysign2.R;

public class MainActivity extends Activity implements CvCameraViewListener2, TextToSpeech.OnInitListener {
	
	private static final String TAG = "ReadMySign::Activity";

    //Color Space used for hand segmentation
	private static final int COLOR_SPACE = Imgproc.COLOR_RGB2Lab;

    //Number of frames collected for each gesture in the training set
	private static final int GES_FRAME_MAX= 10;
	
	public final Object sync = new Object();
	
	//Mode that presamples hand colors
	public static final int SAMPLE_MODE = 0;
	
	//Mode that generates binary image
	public static final int DETECTION_MODE = 1; 
	
	//Mode that displays color image together with contours, fingertips,
	//defect points and so on.
	public static final int TRAIN_REC_MODE = 2;
	
	//Mode that presamples background colors
	public static final int BACKGROUND_MODE = 3;
	
	//Mode that is started when user clicks the 'Add Gesture' button.
	public static final int ADD_MODE = 4;    
	
	//Mode that is started when user clicks the 'Test' button.
	public static final int TEST_MODE = 5;    
	
	//Mode that is started when user clicks 'App Test' in the menu.
	public static final int APP_TEST_MODE = 6;  
	
	//Mode that is started when user clicks 'Data Collection' in the menu.
	public static final int DATA_COLLECTION_MODE = 0; 
	
	//Mode that is started when user clicks 'Map Apps' in the menu.
	public static final int MAP_APPS_MODE = 1;   
	
	//Number of frames used for prediction
	private static final int FRAME_BUFFER_NUM = 1; 
	
	//Frame interval between two launching events

	private boolean isPictureSaved = false;
	private int appTestFrameCount = 0;


	
	// onActivityResult request


	
	private String storeFolderName = null;
	private File storeFolder = null;


    //Stores the mapping results from gesture labels to app intents
	private HashMap<Integer, Intent> table = new HashMap<Integer, Intent>();
		   
	private CameraView mOpenCvCameraView;

	private List<android.hardware.Camera.Size> mResolutionList;
	
	//Initial mode is BACKGROUND_MODE to presample the colors of the hand
	private int mode = BACKGROUND_MODE;
	

	
	private static final int SAMPLE_NUM = 7;
		
	private Point[][] samplePoints = null;
	private double[][] avgColor = null;
	private double[][] avgBackColor = null;

	private ArrayList<ArrayList<Double>> averChans = new ArrayList<ArrayList<Double>>();
	
	private double[][] cLower = new double[SAMPLE_NUM][3];
	private double[][] cUpper = new double[SAMPLE_NUM][3];
	private double[][] cBackLower = new double[SAMPLE_NUM][3];
	private double[][] cBackUpper = new double[SAMPLE_NUM][3];
	
	private Scalar lowerBound = new Scalar(0, 0, 0);
	private Scalar upperBound = new Scalar(0, 0, 0);
	private int squareLen;
	
	private Mat sampleColorMat = null;
	private List<Mat> sampleColorMats = null;
	
	private Mat[] sampleMats = null ;
	
	private Mat rgbaMat = null;
	
	private Mat rgbMat = null;
	private Mat bgrMat = null;
	
	
	private Mat interMat = null;

	private Mat binMat = null;
	private Mat binTmpMat = null;
	private Mat binTmpMat2 = null;
	private Mat binTmpMat0 = null;
	private Mat binTmpMat3 = null;
	
	private Mat tmpMat = null;
	private Mat backMat = null;
	private Mat difMat = null;
	private Mat binDifMat = null;
	    
    private Scalar  mColorsRGB[] = null;
    
    //Stores all the information about the hand


	private int imgNum;
	private int gesFrameCount;
	private int curLabel = 0;
	private int selectedLabel = -2;
	private int curMaxLabel = 0;

	

	
	File sdCardDir = Environment.getExternalStorageDirectory();
	File sdFile = new File(sdCardDir, "AppMap.txt"); 


	private TextToSpeech engine;
	
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch(status) {
			case LoaderCallbackInterface.SUCCESS: {
			System.out.print("Loaded successfully");
				
				System.loadLibrary("ReadMySign");
				
				  try {
			            System.loadLibrary("signal");
			        } catch (UnsatisfiedLinkError ule) {

					  System.out.print("Loaded faild");

			        }
				  
				mOpenCvCameraView.enableView();
				
				 mOpenCvCameraView.setOnTouchListener(new OnTouchListener() {
					 

	    	    	    public boolean onTouch(View v, MotionEvent event) {

	    	    	    	 int action = MotionEventCompat.getActionMasked(event);
	    	    	         
	    	    	    	    switch(action) {
	    	    	    	        case (MotionEvent.ACTION_DOWN) :
	    	    	    	            Log.d(TAG,"Action was DOWN");
	    	    	    	            String toastStr = null;
	    	    	    	            if (mode == SAMPLE_MODE) {
	    	    	    	            	mode = DETECTION_MODE;
	    	    	    	            	toastStr = "Sampling Finished!";
	    	    	    	            } else if (mode == DETECTION_MODE) {
	    	    	    	            	mode = TRAIN_REC_MODE;
	    	    	    	         	    	    	    	            	

	    	    	    	            	toastStr = "Binary Display Finished!";
	    	    	    	            	
	    	    	    	            	preTrain();
	    	    	    	            	
	    	    	    	            } else if (mode == TRAIN_REC_MODE){
	    	    	    	            	mode = DETECTION_MODE;
	    	    	    	            	

	    	    	    	            	

	    	    	    	            } else if (mode == BACKGROUND_MODE) {
	    	    	    	            	toastStr = "First background sampled!";
	    	    	    	            	rgbaMat.copyTo(backMat);
	    	    	    	            	mode = SAMPLE_MODE;
	    	    	    	            }
	    	    	    	            
	    	    	    	        	Toast.makeText(getApplicationContext(), toastStr, Toast.LENGTH_LONG).show();
	    	    	    	            return false;
	    	    	    	        case (MotionEvent.ACTION_MOVE) :
	    	    	    	            Log.d(TAG,"Action was MOVE");
	    	    	    	            return true;
	    	    	    	        case (MotionEvent.ACTION_UP) :
	    	    	    	            Log.d(TAG,"Action was UP");
	    	    	    	            return true;
	    	    	    	        case (MotionEvent.ACTION_CANCEL) :
	    	    	    	            Log.d(TAG,"Action was CANCEL");
	    	    	    	            return true;
	    	    	    	        case (MotionEvent.ACTION_OUTSIDE) :
	    	    	    	            Log.d(TAG,"Movement occurred outside bounds " +
	    	    	    	                    "of current screen element");
	    	    	    	            return true;      
	    	    	    	        default : 
	    	    	    	            return true;
	    	    	    	    }   
    	    	    	    }
	    	     });
				 
			} break;
			default: {
				super.onManagerConnected(status);
				
			}break;
			}
		}
	};
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		engine = new TextToSpeech(this, this);
		try{
	        FileInputStream fis = new FileInputStream(sdFile);
	        ObjectInputStream ois = new ObjectInputStream(fis);
	        while(true) {
	             try{	            
	                   int key = ois.readInt();
	                   String value =(String) ois.readObject();

	                   Intent intent = Intent.parseUri(value, 0);
	                   table.put(key, intent);
	             }
	             catch(IOException e)
	             {
	                  break;
	             }
	        }  
	        ois.close();
	        Log.e("ReadFile","read succeeded......");
        }catch(Exception ex) {
        	Log.e("ReadFile","read ended......");
        	
        }
		
		mOpenCvCameraView = (CameraView) findViewById(R.id.maincameraview);
		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);
		
		samplePoints = new Point[SAMPLE_NUM][2];
		for (int i = 0; i < SAMPLE_NUM; i++){
			for (int j = 0; j < 2; j++)	{
				samplePoints[i][j] = new Point();
			}
		}
		
		avgColor = new double[SAMPLE_NUM][3];
		avgBackColor = new double[SAMPLE_NUM][3];
		
		for (int i = 0; i < 3; i++)
			averChans.add(new ArrayList<Double>());
		

		initCLowerUpper(50, 50, 10, 10, 10, 10);
	    initCBackLowerUpper(50, 50, 3, 3, 3, 3);
		
		SharedPreferences numbers = getSharedPreferences("Numbers", 0);
        imgNum = numbers.getInt("imgNum", 0);
		
        
        
        initOpenCV();
        

	}
	
	@Override
    public void onInit(int status) {


        if (status == TextToSpeech.SUCCESS) {

            engine.setLanguage(Locale.UK);
        }
    }


	public void initOpenCV() {
		
	}


	

 	public boolean isExternalStorageWritable() {
 	    String state = Environment.getExternalStorageState();
 	    if (Environment.MEDIA_MOUNTED.equals(state)) {
 	        return true;
 	    }
 	    return false;
 	}
 	 	




 	public void preTrain() {
 		 if (!isExternalStorageWritable()) {
 			 Toast.makeText(getApplicationContext(), "External storage is not writable!", Toast.LENGTH_SHORT).show();
		 } else if (storeFolder == null) {
 			storeFolderName = Environment.getExternalStorageDirectory() + "/ReadMySign";
 			storeFolder = new File(storeFolderName);
 			boolean success = true;
 			if (!storeFolder.exists()) {
 			    success = storeFolder.mkdir();
 			}
 			if (success) {
 			    // Do something on success

 			} else {
 				Toast.makeText(getApplicationContext(), "Failed to create directory "+ storeFolderName, Toast.LENGTH_SHORT).show();
 				storeFolder = null;
 				storeFolderName = null;
 			}
 		}

 		 if (storeFolder != null) {

 		 }
 	}


 	

	

	void initCLowerUpper(double cl1, double cu1, double cl2, double cu2, double cl3,
			double cu3)
	{
		cLower[0][0] = cl1;
		cUpper[0][0] = cu1;
		cLower[0][1] = cl2;
		cUpper[0][1] = cu2;
		cLower[0][2] = cl3;
		cUpper[0][2] = cu3;
	}
	
	void initCBackLowerUpper(double cl1, double cu1, double cl2, double cu2, double cl3,
			double cu3)
	{
		cBackLower[0][0] = cl1;
		cBackUpper[0][0] = cu1;
		cBackLower[0][1] = cl2;
		cBackUpper[0][1] = cu2;
		cBackLower[0][2] = cl3;
		cBackUpper[0][2] = cu3;
	}
		


	
	public void releaseCVMats() {
		releaseCVMat(sampleColorMat);
		sampleColorMat = null;
		
		if (sampleColorMats!=null) {
			for (int i = 0; i < sampleColorMats.size(); i++)
			{
				releaseCVMat(sampleColorMats.get(i));
			
			}
		}
		sampleColorMats = null;
		
		if (sampleMats != null) {
			for (int i = 0; i < sampleMats.length; i++)
			{
				releaseCVMat(sampleMats[i]);
			}
		}
		sampleMats = null;
		
		releaseCVMat(rgbMat);
		rgbMat = null;
		
		releaseCVMat(bgrMat);
		bgrMat = null;
		
		releaseCVMat(interMat);
		interMat = null;
		
		releaseCVMat(binMat);
		binMat = null;
		
		releaseCVMat(binTmpMat0);
		binTmpMat0 = null;
		
		releaseCVMat(binTmpMat3);
		binTmpMat3 = null;
		
		releaseCVMat(binTmpMat2);
		binTmpMat2 = null;
		
		releaseCVMat(tmpMat);
		tmpMat = null;
		
		releaseCVMat(backMat);
		backMat = null;
		
		releaseCVMat(difMat);
		difMat = null;
		
		releaseCVMat(binDifMat);
		binDifMat = null;
		
	}
	
	public void releaseCVMat(Mat img) {
		if (img != null)
			img.release();
	}
	
	@Override
	public void onCameraViewStarted(int width, int height) {
		// TODO Auto-generated method stub
		Log.i(TAG, "On cameraview started!");
					
		if (sampleColorMat == null)
		sampleColorMat = new Mat();
			
		if (sampleColorMats == null)
		sampleColorMats = new ArrayList<Mat>();
		
		if (sampleMats == null) {
		sampleMats = new Mat[SAMPLE_NUM];
		for (int i = 0; i < SAMPLE_NUM; i++)
			sampleMats[i] = new Mat();
		}
		
		if (rgbMat == null)
		rgbMat = new Mat();
		
		if (bgrMat == null)
		bgrMat = new Mat();
		
		if (interMat == null)
		interMat = new Mat();
		
		if (binMat == null)
		binMat = new Mat();
		
		if (binTmpMat == null)
		binTmpMat = new Mat();
		
		if (binTmpMat2 == null)
		binTmpMat2 = new Mat();
		
		if (binTmpMat0 == null)
		binTmpMat0 = new Mat();
		
		if (binTmpMat3 == null)
		binTmpMat3 = new Mat();
		
		if (tmpMat == null)
		tmpMat = new Mat();
		
		if (backMat==null)
		backMat = new Mat();
		
		if (difMat == null)
		difMat = new Mat();
		
		if (binDifMat == null)
		binDifMat = new Mat();
		


	    
        mColorsRGB = new Scalar[] { new Scalar(255, 0, 0, 255), new Scalar(0, 255, 0, 255), new Scalar(0, 0, 255, 255) };
        
	}

	@Override
	public void onCameraViewStopped() {
		// TODO Auto-generated method stub
		Log.i(TAG, "On cameraview stopped!");

	}

	

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		rgbaMat = inputFrame.rgba();
				

				
		Imgproc.GaussianBlur(rgbaMat, rgbaMat, new Size(5,5), 5, 5);
		
		Imgproc.cvtColor(rgbaMat, rgbMat, Imgproc.COLOR_RGBA2RGB);
		

		Imgproc.cvtColor(rgbaMat, interMat, COLOR_SPACE);
				
		if (mode == SAMPLE_MODE) {
		
			preSampleHand(rgbaMat);
			
		} else if (mode == DETECTION_MODE) {
			produceBinImg(interMat, binMat);

			return binMat;

		} else if ((mode == TRAIN_REC_MODE)||(mode == ADD_MODE)
		|| (mode == TEST_MODE) || (mode == APP_TEST_MODE)){




		} else if (mode == BACKGROUND_MODE) {
			preSampleBack(rgbaMat);
		}

		if (isPictureSaved) {

			isPictureSaved = false;
		}
		return rgbaMat;
	}



	void preSampleHand(Mat img)
	{
		int cols = img.cols();
		int rows = img.rows();
		squareLen = rows/20;
		Scalar color = mColorsRGB[2];


		samplePoints[0][0].x = cols/2;
		samplePoints[0][0].y = rows/4;
		samplePoints[1][0].x = cols*5/12;
		samplePoints[1][0].y = rows*5/12;
		samplePoints[2][0].x = cols*7/12;
		samplePoints[2][0].y = rows*5/12;
		samplePoints[3][0].x = cols/2;
		samplePoints[3][0].y = rows*7/12;
		samplePoints[4][0].x = cols*1/3;
		samplePoints[4][0].y = rows*7/12;
		samplePoints[5][0].x = cols*4/9;
		samplePoints[5][0].y = rows*3/4;
		samplePoints[6][0].x = cols*5/9;
		samplePoints[6][0].y = rows*3/4;

		for (int i = 0; i < SAMPLE_NUM; i++)
		{
			samplePoints[i][1].x = samplePoints[i][0].x+squareLen;
			samplePoints[i][1].y = samplePoints[i][0].y+squareLen;
		}

		for (int i = 0; i < SAMPLE_NUM; i++)
		{
			Core.rectangle(img,  samplePoints[i][0], samplePoints[i][1], color, 1);
		}

		for (int i = 0; i < SAMPLE_NUM; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				avgColor[i][j] = (interMat.get((int)(samplePoints[i][0].y+squareLen/2), (int)(samplePoints[i][0].x+squareLen/2)))[j];
			}
		}
	}

	void preSampleBack(Mat img)
	{
		int cols = img.cols();
		int rows = img.rows();
		squareLen = rows/20;
		Scalar color = mColorsRGB[2];

		samplePoints[0][0].x = cols/6;
		samplePoints[0][0].y = rows/3;
		samplePoints[1][0].x = cols/6;
		samplePoints[1][0].y = rows*2/3;
		samplePoints[2][0].x = cols/2;
		samplePoints[2][0].y = rows/6;
		samplePoints[3][0].x = cols/2;
		samplePoints[3][0].y = rows/2;
		samplePoints[4][0].x = cols/2;
		samplePoints[4][0].y = rows*5/6;
		samplePoints[5][0].x = cols*5/6;
		samplePoints[5][0].y = rows/3;
		samplePoints[6][0].x = cols*5/6;
		samplePoints[6][0].y = rows*2/3;

		for (int i = 0; i < SAMPLE_NUM; i++)
		{
			samplePoints[i][1].x = samplePoints[i][0].x+squareLen;
			samplePoints[i][1].y = samplePoints[i][0].y+squareLen;
		}

		for (int i = 0; i < SAMPLE_NUM; i++)
		{
			Core.rectangle(img,  samplePoints[i][0], samplePoints[i][1], color, 1);
		}

		for (int i = 0; i < SAMPLE_NUM; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				avgBackColor[i][j] = (interMat.get((int)(samplePoints[i][0].y+squareLen/2), (int)(samplePoints[i][0].x+squareLen/2)))[j];
			}
		}

	}
		
	void boundariesCorrection()
	{
		for (int i = 1; i < SAMPLE_NUM; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				cLower[i][j] = cLower[0][j];
				cUpper[i][j] = cUpper[0][j];

				cBackLower[i][j] = cBackLower[0][j];
				cBackUpper[i][j] = cBackUpper[0][j];
			}
		}

		for (int i = 0; i < SAMPLE_NUM; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				if (avgColor[i][j] - cLower[i][j] < 0)
					cLower[i][j] = avgColor[i][j];

				if (avgColor[i][j] + cUpper[i][j] > 255)
					cUpper[i][j] = 255 - avgColor[i][j];

				if (avgBackColor[i][j] - cBackLower[i][j] < 0)
					cBackLower[i][j] = avgBackColor[i][j];

				if (avgBackColor[i][j] + cBackUpper[i][j] > 255)
					cBackUpper[i][j] = 255 - avgBackColor[i][j];
			}
		}
	}


	

	void produceBinImg(Mat imgIn, Mat imgOut)
	{
		boundariesCorrection();
		
		produceBinHandImg(imgIn, binTmpMat);
			
		produceBinBackImg(imgIn, binTmpMat2);
			
		Core.bitwise_and(binTmpMat, binTmpMat2, binTmpMat);
		binTmpMat.copyTo(tmpMat);
		binTmpMat.copyTo(imgOut);

		
	}
	

	void produceBinHandImg(Mat imgIn, Mat imgOut)
	{
		for (int i = 0; i < SAMPLE_NUM; i++)
		{
			lowerBound.set(new double[]{avgColor[i][0]-cLower[i][0], avgColor[i][1]-cLower[i][1],
					avgColor[i][2]-cLower[i][2]});
			upperBound.set(new double[]{avgColor[i][0]+cUpper[i][0], avgColor[i][1]+cUpper[i][1],
					avgColor[i][2]+cUpper[i][2]});
			
			Core.inRange(imgIn, lowerBound, upperBound, sampleMats[i]);			
			
		}
		
		imgOut.release();
		sampleMats[0].copyTo(imgOut);
			
		for (int i = 1; i < SAMPLE_NUM; i++)
		{
			Core.add(imgOut, sampleMats[i], imgOut);
		}
		
		Imgproc.medianBlur(imgOut, imgOut, 3);
	}
	

	void produceBinBackImg(Mat imgIn, Mat imgOut) {
		for (int i = 0; i < SAMPLE_NUM; i++) {
					
			lowerBound.set(new double[]{avgBackColor[i][0]-cBackLower[i][0], avgBackColor[i][1]-cBackLower[i][1],
					avgBackColor[i][2]-cBackLower[i][2]});
			upperBound.set(new double[]{avgBackColor[i][0]+cBackUpper[i][0], avgBackColor[i][1]+cBackUpper[i][1],
					avgBackColor[i][2]+cBackUpper[i][2]});
			Core.inRange(imgIn, lowerBound, upperBound, sampleMats[i]);
		}
		
		imgOut.release();
		sampleMats[0].copyTo(imgOut);
	
		for (int i = 1; i < SAMPLE_NUM; i++) {
			Core.add(imgOut, sampleMats[i], imgOut);
		}
		
		Core.bitwise_not(imgOut, imgOut);
		Imgproc.medianBlur(imgOut, imgOut, 7);		
	}


	
	
	@Override
	public void onPause(){
		Log.i(TAG, "Paused!");
		super.onPause();
		if (mOpenCvCameraView != null){
			mOpenCvCameraView.disableView();
		}
	}
	
	@Override
	public void onResume() {		
		super.onResume();		
		
		if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
	
		Log.i(TAG, "Resumed!");
	}
	
	@Override
	public void onDestroy(){
		Log.i(TAG, "Destroyed!");
		releaseCVMats();

		super.onDestroy();
		if (mOpenCvCameraView != null) {
			mOpenCvCameraView.disableView();
		}

	     }
	

}