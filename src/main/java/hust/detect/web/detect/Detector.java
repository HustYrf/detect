package hust.detect.web.detect;


/**
 * 该类用于识别图片 
   暂时废弃，当前使用自动识别方案
 */
public class Detector {
	
	public static final String LibPath = "/home/gxdx/temp/darknet-master";
	public static final String CudaLibPath = "/usr/local/cuda-10.0/lib64";

	public static native void run(String loadPath, String savePath);

	static {
		System.load(CudaLibPath + "/" + "libcublas.so.10.0");
		System.load(CudaLibPath + "/" + "libcudart.so.10.0");
		System.load(CudaLibPath + "/" + "libcudnn.so.7");
		System.load(CudaLibPath + "/" + "libcurand.so.10.0");
		System.load(CudaLibPath + "/" + "libcusolver.so.10.0");
		System.load(LibPath + "/" + "darknet.so");
	}

//	public static void main( String[] args )
//	{
//		Detector detector = new Detector();
//		detector.run("/home/gxdx/test", "/home/gxdx/result");
//	}

}
