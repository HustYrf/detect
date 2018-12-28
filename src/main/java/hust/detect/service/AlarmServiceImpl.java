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

	// 读取文件夹里面的文件，并且生成缩略图文件，命名为：原图片名--thumbnail.jpg  例如：5.jpg  ->  5-thumbnail.jpg
	public List<Alarm> processlcoaldir(int taskid, String alarmDir) {

		List<Alarm> alarmList = new ArrayList<Alarm>();
		File file = new File(alarmDir);

		if (file.exists()) {
			File[] files = file.listFiles();
			for (File file2 : files) {
				if (file2.isDirectory()) {
				} else {
					Alarm alarm = new Alarm();
					alarm.setUpdatetime(new Date());
					alarm.setStatus(0);// 未处理告警
					alarm.setTaskId(taskid);
					alarm.setImageurl(file2.getName());
					
					
					int pointPosition = file2.getName().lastIndexOf(".");
					StringBuffer stringBuffer = new StringBuffer(file2.getName().substring(0, pointPosition));
					stringBuffer.append("-thumbnail.");
					stringBuffer.append(file2.getName().substring(pointPosition+1, file2.getName().length()));
					
					alarm.setThumbnailurl(stringBuffer.toString());

					try {
						Metadata metadata = ImageMetadataReader.readMetadata(file2);
						for (Directory directory : metadata.getDirectories()) {
							for (Tag tag : directory.getTags()) {
								String tagName = tag.getTagName(); // 标签名
								String desc = tag.getDescription(); // 标签信息
								if (tagName.equals("Date/Time Original")) {
									alarm.setCreatetime(DateKit.stringToDate(desc));
								}
							}
						}

						// 暂时把创建时间设为当前时间
						alarm.setCreatetime(new Date());

						byte[] imgbyte = image2Bytes(file2);
						int length = imgbyte.length;
						byte[] position = new byte[12];
						for (int i = 0; i < 12; i++) {
							position[i] = imgbyte[length - 12 + i];
						}

						float lat = getFloat(position, 0);
						float lon = getFloat(position, 4);
						float elv = getFloat(position, 8);

						RouteExcel routeExcel = new RouteExcel();

						routeExcel.setLatitude((double) lat);
						routeExcel.setLongitude((double) lon);

						alarm.setPosition(routeExcel.getPositon());

						alarm.setDescription("这是一张无人机从" + elv + " 高度拍摄告警照片！");

						// 在这里匹配最近的信息点
						List<String> geohash9area = GeohashUtil.getGeoHashBase32For9(routeExcel);

						List<InfoPoint> infoPoints = infoPointMapper.getNearPointByGeohash(geohash9area);
						if (infoPoints.size() == 0) {
							alarm.setInfoname(null);
						} else {
							InfoPoint infoPoint = getMinDisInfoPoint(routeExcel, infoPoints);
							alarm.setInfoname(infoPoint.getName());
							alarm.setRouteId(infoPoint.getRouteId());
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
		
		//生成缩略图
		try {
			Thumbnails.of(new File(alarmDir).listFiles()).
				scale(0.5). // 图片缩放80%, 不能和size()一起使用
				toFiles(new File(alarmDir), Rename.SUFFIX_HYPHEN_THUMBNAIL);// 指定的目录一定要存在,否则报错
		} catch (IOException e) {
			e.printStackTrace();
		}

		return alarmList;
	}

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

	static public byte[] image2Bytes(File imgSrc) throws Exception {
		FileInputStream fin = new FileInputStream(imgSrc);
		// 可能溢出,简单起见就不考虑太多,如果太大就要另外想办法，比如一次传入固定长度byte[]
		byte[] bytes = new byte[fin.available()];
		// 将文件内容写入字节数组，提供测试的case
		fin.read(bytes);
		fin.close();
		return bytes;
	}

}
