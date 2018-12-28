package hust.detect.dao.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import hust.detect.dao.pojo.InfoPoint;

public interface InfoPointMapper {

    List<InfoPoint> getNearPointByGeohash(@Param("geohashs")List<String> geohashs);

    int insertInfoPointList(@Param("infoPoints")List<InfoPoint> infoPoints);

    List<InfoPoint> selectAllInfoPoint();
}