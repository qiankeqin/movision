package com.movision.mybatis.activeH5.mapper;

import com.movision.mybatis.activeH5.entity.ActiveH5;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

public interface ActiveH5Mapper {
    int deleteByPrimaryKey(Integer id);

    int insert(ActiveH5 record);

    int insertSelective(ActiveH5 record);

    ActiveH5 selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(ActiveH5 record);

    int updateByPrimaryKey(ActiveH5 record);

    int deleteActive(int id);

    List<ActiveH5> findAllActive(RowBounds rowBounds);

}