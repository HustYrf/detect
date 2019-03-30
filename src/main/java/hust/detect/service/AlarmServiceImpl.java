package hust.detect.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import hust.detect.common.DateKit;
import hust.detect.common.GeohashUtil;
import hust.detect.common.GetLonLat;
import hust.detect.common.MapUtils;
import hust.detect.common.PointUtil;
import hust.detect.common.RouteExcel;
import hust.detect.dao.mapper.AlarmMapper;
import hust.detect.dao.mapper.InfoPointMapper;
import hust.detect.dao.pojo.Alarm;
import hust.detect.dao.pojo.InfoPoint;
import hust.detect.service.interFace.AlarmService;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.name.Rename;

@Service
public class AlarmServiceImpl implements AlarmService {

	@Autowired
	private AlarmMapper AlarmMapper;

	@Autowired
	private InfoPointMapper infoPointMapper;

	@Override
	public boolean insertAlarm(Integer taskId, String alarmDir) {
		List<Alarm> alarmList = processlcoaldir(taskId, alarmDir);
		Iterator<Alarm> iterator = alarmList.iterator();
		while (iterator.hasNext()) {
			Alarm alarm = iterator.next();
			AlarmMapper.insertAlarmSelective(alarm);
		}
		return true;
	}
	
	// 读取文件夹里面的文件，并且生成缩略图文件，命名为：原图片名--thumbnail.jpg 例如：5.jpg -> 5-thumbnail.jpg
	public List<Alarm> processlcoaldir(int taskid, String alarmDir) {
//		taskid = 233;
//		alarmDir = "C:\\Users\\Zyh\\Desktop\\1902260310";  //测试
		
		List<Alarm> alarmList = new ArrayList<Alarm>();
		File file = new File(alarmDir);

		if (file.exists()) { // 阻塞监听是否识别完成
			boolean flag = true;
			while (flag) {
				File[] waitfiles = file.listFiles();
				for (File file4 : waitfiles) {
					if (!file4.isDirectory()) {
						if (file4.getName().equals("end.jpg")) {
							flag = false;
							break;
						}
					}
				}
			}
		}

		if (file.exists()) {
			File[] files = file.listFiles();

			for (File file2 : files) {
				if (file2.isDirectory()) {
				} else {
					if (file2.getName().equals("end.jpg")) {
						continue; // 如果是结尾图片则不读取
					}
					//生成缩略图 放到同一个文件夹里面
					try {
						Thumbnails.of(file2).scale(0.5). // 图片缩放50%, 不能和size()一起使用
								toFiles(new File(alarmDir), Rename.SUFFIX_HYPHEN_THUMBNAIL);
					} catch (IOException e1) {
						e1.printStackTrace();
					}

					Alarm alarm = new Alarm();
					alarm.setUpdatetime(new Date());
					alarm.setStatus(0);// 未处理告警
					alarm.setTaskId(taskid);
					alarm.setImageurl(file2.getName());

					// 生成缩略图文件并且保存在同一个目录里面
					int pointPosition = file2.getName().lastIndexOf(".");
					StringBuffer stringBuffer = new StringBuffer(file2.getName().substring(0, pointPosition));
					stringBuffer.append("-thumbnail.");
					stringBuffer.append(file2.getName().substring(pointPosition + 1, file2.getName().length()));

					alarm.setThumbnailurl(stringBuffer.toString());

					try {
						Metadata metadata = ImageMetadataReader.readMetadata(file2);
						
						Double lat = 0.0;
						Double lon = 0.0;
						Double elv = 0.0;
						
						Double hdg = 0.0;
						Double hori = 0.0;
						Double vert = 0.0;
						Double camera = 0.0;
		                /*
						 * 读取照片标签信息获得经纬高
						 */
						for (Directory directory : metadata.getDirectories()) {
							for (Tag tag : directory.getTags()) {
								String tagName = tag.getTagName(); // 标签名
								String desc = tag.getDescription(); // 标签信息
								//System.out.println(tagName+"   "+desc);//照片信息
								if (tagName.equals("Date/Time Original")) {
									alarm.setCreatetime(DateKit.stringToDate(desc));
								//读取EXIF标签信息
								} else if (tagName.equals("GPS Latitude")) {   //纬度
		                             lat = pointToLatlong(desc);
		                        } else if (tagName.equals("GPS Longitude")) {  //经度
		                             lon = pointToLatlong(desc);
		                        } else if (tagName.equals("GPS Altitude")) {   //高度
		                        	 elv = tagToDouble(desc);
		                        } else if (tagName.equals("GPS Speed")) { 	 // 航向角
									hdg = Double.parseDouble(desc);
								} else if (tagName.equals("GPS Track")) { 	 // 横滚角(取绝对值)
									hori = Math.abs(tagToDouble(desc));
								} else if (tagName.equals("GPS Img Direction")) { // 俯仰角
									vert = tagToDouble(desc);
								} else if (tagName.equals("GPS DOP")) { // 相机角
									camera = Double.parseDouble(desc);
								}
							}
						}

						// 暂时把创建时间设为当前时间
						alarm.setCreatetime(new Date());
						
						/*
						 * 如果没有gps信息
						 * 读取照片最后12个字节获得经纬高
						 */
						if(lat==0.0) {
							byte[] imgbyte = image2Bytes(file2);
							int length = imgbyte.length;
							byte[] position = new byte[12];
							for (int i = 0; i < 12; i++) {
								position[i] = imgbyte[length - 12 + i];
							}

							elv = (double) getFloat(position, 0);
							lat = (double) getFloat(position, 4);
							lon = (double) getFloat(position, 8);
						}
						
						// 相机角和俯仰角可以相互叠加，俯仰角机头抬起为正，反之为负
						String p = file2.getName();
						System.out.println("\n文件名："+p);
						Double b1 = elv * Math.tan((camera+vert)*Math.PI /180);
						System.out.println("b1: "+b1);
						Double b2 = elv * Math.tan(hori*Math.PI /180);
						System.out.println("b2: "+b2);
						// 两点之间距离
						Double s = Math.sqrt(b1*b1 + b2*b2);
						System.out.println("距离s: "+s);
						// 已知一点经纬度，方位角，距离，求另一点经纬度
						List<Double> list = GetLonLat.computerThatLonLat(lon, lat, hdg, s);
						Double newLon = list.get(0);
						Double newLat = list.get(1);
						System.out.println("newLon: "+newLon);
						System.out.println("newLat: "+newLat);
						
						System.out.println("经度longitude:"+lon);
		    			System.out.println("纬度latitude:"+lat);
		    			System.out.println("海拔elevation:"+elv);

						RouteExcel routeExcel = new RouteExcel();

						routeExcel.setLatitude(newLon);
						routeExcel.setLongitude(newLat);

						alarm.setPosition(routeExcel.getPositon());

						alarm.setDescription("这是一张无人机从" + elv + " 高度拍摄告警照片！");

						// 在这里匹配最近的信息点
						List<String> geohash9area = GeohashUtil.getGeoHashBase32For9(routeExcel);
						if(geohash9area.size()>0) {
							List<InfoPoint> infoPoints = infoPointMapper.getNearPointByGeohash(geohash9area);
							InfoPoint infoPoint = getMinDisInfoPoint(routeExcel, infoPoints);
							alarm.setInfoname(infoPoint.getName());
							alarm.setRouteId(infoPoint.getRouteId());
						}else {
							alarm.setInfoname(null);
							alarm.setRouteId(null);
						}
						
						alarmList.add(alarm);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

		} else {
			System.out.println("文件不存在!");
		}

		return alarmList;
	}

	/*public static void main(String[] args) {

		File file = new File("D:\\pic");
		if (file.exists()) {
			File[] files = file.listFiles();
			for (File file2 : files) {
				if (file2.isDirectory()) {
				} else {
					if (file2.getName().equals("end.jpg")) {
						continue; // 如果是结尾图片则不读取
					}					
					try {
						Thumbnails.of(file2).scale(0.5). // 图片缩放50%, 不能和size()一起使用
								toFiles(new File("D:\\pic"), Rename.SUFFIX_HYPHEN_THUMBNAIL);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		}
	}*/

	// 查找离 坐标最近的信息点
	public static InfoPoint getMinDisInfoPoint(RouteExcel routeExcel, List<InfoPoint> infoPoints) {

		Double minDis = Double.MAX_VALUE;
		InfoPoint minInfoPoint = null;

		for (int i = 0; i < infoPoints.size(); i++) {
			InfoPoint infoPoint = infoPoints.get(i);
			List<Double> position = PointUtil.StringPointToList(infoPoint.getPosition());
			Double dis = MapUtils.GetDistance(routeExcel.getLongitude(), routeExcel.getLatitude(), position.get(0),
					position.get(1));

			if (minDis > dis) {
				minInfoPoint = infoPoint;
				minDis = dis;
			}
		}
		return minInfoPoint;
	}

	public static float getFloat(byte[] b, int index) {

		byte sndlen[] = new byte[4];
		sndlen[3] = b[index + 3];
		sndlen[2] = b[index + 2];
		sndlen[1] = b[index + 1];
		sndlen[0] = b[index + 0];
		long data = (long) (Byte2Int(sndlen) & 0x0FFFFFFFFl);
		// 获取长度
		return data / 10000000.0f; // 把float字节码转换成float
	}

	public static int Byte2Int(byte[] bytes) {

		return (bytes[0] & 0xff) << 24 | (bytes[1] & 0xff) << 16 | (bytes[2] & 0xff) << 8 | (bytes[3] & 0xff);
	}

	public static byte[] image2Bytes(File imgSrc) throws Exception {
		FileInputStream fin = new FileInputStream(imgSrc);
		// 可能溢出,简单起见就不考虑太多,如果太大就要另外想办法，比如一次传入固定长度byte[]
		byte[] bytes = new byte[fin.available()];
		// 将文件内容写入字节数组，提供测试的case
		fin.read(bytes);
		fin.close();
		return bytes;
	}
	
	/** 
     * 经纬度格式  转换
     * @param point 坐标点 
     * @return 
     */ 
    public static Double pointToLatlong (String point ) {  
        Double du = Double.parseDouble(point.substring(0, point.indexOf("°")).trim());  
        Double fen = Double.parseDouble(point.substring(point.indexOf("°")+1, point.indexOf("'")).trim());  
        Double miao = Double.parseDouble(point.substring(point.indexOf("'")+1, point.indexOf("\"")).trim());  
        Double duStr = du + fen / 60 + miao / 60 / 60 ;  
        return duStr;  
    }
    
    // String转Double
    public static Double tagToDouble(String desc) {
    	Double alt = Double.parseDouble(desc.split(" ")[0]);
		return alt;
	}
}
