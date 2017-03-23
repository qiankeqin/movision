package com.movision.mybatis.compressImg.mapper;

import com.movision.mybatis.compressImg.entity.CompressImg;
import org.springframework.stereotype.Repository;

@Repository
public interface CompressImgMapper {
    int insert(CompressImg record);

    int insertSelective(CompressImg record);

    int queryCount(CompressImg compressImg);
}