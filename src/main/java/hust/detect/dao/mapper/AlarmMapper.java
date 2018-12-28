package hust.detect.dao.mapper;

import org.apache.ibatis.annotations.Param;
import hust.detect.dao.pojo.Alarm;
import java.util.List;

public interface AlarmMapper {

	List<Alarm> selectALLAlarm();
	List<Alarm> selectAllAlarmByCreateTimeDesc();
	int alarmCount(Alarm alarm);
    Alarm selectInfoById(Integer id);
	int updateByAlarmId(Integer id);
    int insertAlarmSelective(Alarm alarm);
	int updateDesByAlarmId(Integer id, String description);
	List<Alarm> getAlarmsByTaskId(@Param("taskId") Integer taskId);
	
}
