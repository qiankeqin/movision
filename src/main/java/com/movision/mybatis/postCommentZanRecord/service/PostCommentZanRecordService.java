package com.movision.mybatis.postCommentZanRecord.service;

import com.movision.mybatis.PostZanRecord.entity.PostZanRecord;
import com.movision.mybatis.PostZanRecord.entity.ZanRecordVo;
import com.movision.mybatis.comment.entity.Comment;
import com.movision.mybatis.comment.entity.CommentVo;
import com.movision.mybatis.post.service.PostService;
import com.movision.mybatis.postCommentZanRecord.entity.PostCommentZanRecord;
import com.movision.mybatis.postCommentZanRecord.entity.PostCommentZanRecordVo;
import com.movision.mybatis.postCommentZanRecord.mapper.PostCommentZanRecordMapper;
import com.movision.mybatis.user.entity.User;
import com.movision.utils.pagination.model.Paging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * @Author zhanglei
 * @Date 2017/4/6 16:28
 */
@Service
@Transactional
public class PostCommentZanRecordService {

    private static Logger log = LoggerFactory.getLogger(PostCommentZanRecordService.class);

    @Autowired
    private PostCommentZanRecordMapper recordMapper;



    public List<CommentVo> queryComment(Integer commentid) {
        try {
            log.info("查询评论");
            return recordMapper.queryComment(commentid);
        } catch (Exception e) {
            log.error("查询评论失败", e);
            throw e;
        }
    }


    public List<ZanRecordVo> findZan(Integer userid) {
        try {
            log.info("查询所有赞");
            return recordMapper.findZan(userid);
        } catch (Exception e) {
            log.error("查询所有赞失败", e);
            throw e;
        }
    }

    public User queryusers(Integer userid) {
        try {
            log.info("查询用户");
            return recordMapper.queryusers(userid);
        } catch (Exception e) {
            log.error("查询用户失败", e);
            throw e;
        }
    }

    public String queryPostNickname(Integer postid) {
        try {
            log.info("查询用户");
            return recordMapper.queryPostNickname(postid);
        } catch (Exception e) {
            log.error("查询用户失败", e);
            throw e;
        }
    }
}
