package hust.detect.web.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import hust.detect.service.interFace.AlarmService;

@Controller
public class IndexController {

	//本机的文件服务器地址
	@Value("${LOCAL_task_DIR}")
	private String LOCAL_ALARM_DIR; 
	
	@Autowired
	private AlarmService alarmServiceImpl;
	
	// 相应创建文件夹的命令
	@RequestMapping(value = "makeTaskDir", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
	@ResponseBody
	public String makeTaskDir(@RequestParam(value = "missionId") String missionId) {

		StringBuilder taskFileAddress = new StringBuilder();
		taskFileAddress.append(LOCAL_ALARM_DIR).append(missionId);// 任务文件夹地址

		StringBuilder sourceFileAddress = new StringBuilder();
		sourceFileAddress.append(LOCAL_ALARM_DIR).append(missionId).append(File.separator).append("ImageResource");// 源任务文件夹地址

		StringBuilder alarmFileAddress = new StringBuilder();
		alarmFileAddress.append(LOCAL_ALARM_DIR).append(missionId).append(File.separator).append("ImageAlarm");// 告警任务文件夹地址	
		
		File file2 = new File(taskFileAddress.toString());
		if (!file2.exists()) {
			boolean mkdirs = file2.mkdirs();
			if (mkdirs == false) {
				System.out.println("任务基本文件夹创建失败！");
			}
		}else {
			return "success";
		}

		File file3 = new File(sourceFileAddress.toString());
		if (!file3.exists()) {
			boolean mkdirs = file3.mkdirs();
			if (mkdirs == false) {
				System.out.println("任务源文件夹创建失败！");
			}
		}

		File file4 = new File(alarmFileAddress.toString());
		if (!file4.exists()) {
			boolean mkdirs = file4.mkdirs();
			if (mkdirs == false) {
				System.out.println("任务告警文件夹创建失败！");
			}
		}

		File basefile = new File(taskFileAddress.toString()); // 如果文件夹存在的话，那么递归赋权限
		if (basefile.exists()) {
			Process proc = null;
			InputStream stderr = null;
			InputStreamReader isr = null;
			BufferedReader br = null;
			try {
				proc = Runtime.getRuntime().exec("chmod -R 755 " + taskFileAddress.toString());
				stderr = proc.getErrorStream();
				isr = new InputStreamReader(stderr);
				br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null)
					System.out.println(line);
				try {
					int exitVal = proc.waitFor(); // 只是为了等待本地命令执行结束
					if (exitVal == 0) {
						return "success";
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}finally {
					isr.close();
					br.close();
				}
				
				proc = Runtime.getRuntime().exec("chown -R gxdx:gxdx " + taskFileAddress.toString());
				stderr = proc.getErrorStream();
				isr = new InputStreamReader(stderr);
				br = new BufferedReader(isr);
				while ((line = br.readLine()) != null)
					System.out.println(line);
				try {
					int exitVal = proc.waitFor(); // 只是为了等待本地命令执行结束
					if (exitVal == 0) {
						return "success";
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}finally {
					isr.close();
					br.close();
				}										
				
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("任务文件夹及子文件夹权限授予失败！");
			} 
		}

		return "error";
	}

	// 相应识别命令
	@RequestMapping(value = "createAlarm", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
	@ResponseBody
	public String createAlarm(@RequestParam(value = "taskId")Integer taskid,@RequestParam(value = "missionId") String missionId) {

		String alarmDir = LOCAL_ALARM_DIR + missionId + "/ImageAlarm/" ;
		if (alarmServiceImpl.insertAlarm(taskid, alarmDir) == true)
			return "success";
		return "error";
	}
	
	//手机端报告任务完成，在ImageResource文件夹新建一个end.txt文件表明文件上传完毕
	@RequestMapping(value = "reportTaskOver", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
	@ResponseBody
	public String reportTaskOver(@RequestParam(value = "missionId") String missionId) {

		String endfile = LOCAL_ALARM_DIR + missionId + "/ImageResource/"+"end.jpg" ;
		File file = new File(endfile);
		FileWriter fileWritter ;
		try {
			if(!file.exists()){
			       file.createNewFile();
			}
			String reString = "taskover";
			fileWritter = new FileWriter(file.getName(),true);
		    fileWritter.write(reString);
		    fileWritter.close();
		    return "success";
		}catch (Exception e) {
			e.printStackTrace();
			return "error";
		}		
	}
	//返回图片名列表
	@RequestMapping(value = "taskImages")
	@ResponseBody
	public String taskImages(@RequestParam(value = "missionId") String missionId) {
		if (Integer.valueOf(missionId) != null) {
			String picDir = LOCAL_ALARM_DIR + missionId + "/ImageResource/" ;
			List<String> picNameList = null;
			picNameList = getFiles(picDir);
			
			return picNameList.toString().replace("[", "").replace("]"," ").replaceAll(", ", ",");
		
		}else {
			return "null";
		}

	}
	
	//测试项目是否启动成功
	@RequestMapping("home")
	public String home() {
		return "index";
	}
	
	 public List<String> getFiles(String path) {
		 List<String> files = new ArrayList<String>();
	        File file = new File(path);
	        if(!file.exists()){
	            return null;
	        }
	        File[] tempList = file.listFiles();
	        for (int i = 0; i < tempList.length; i++) {
	            if (tempList[i].isFile()) {
            	  if(tempList[i].getName().equals("end.jpg"))   //如果是结束图片则跳过
            		   continue;
	              files.add(tempList[i].getName());
	            }
	        }
	        return files;
	}
	 
//	public static void main(String[] args) {
//		
//		String endfile = "D:\\end.jpg";
//		File file = new File(endfile);
//		try {
//			if(!file.exists()){
//			   file.createNewFile();
//			}	
//		}catch (Exception e) {
//			e.printStackTrace();
//		}
//	}

}
