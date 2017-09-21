package com.movision.controller.boss.robot;

import com.movision.common.Response;
import com.movision.facade.robot.RobotFacade;
import com.movision.mybatis.record.entity.Record;
import com.movision.mybatis.user.entity.User;
import com.movision.mybatis.user.entity.UserVo;
import com.movision.utils.pagination.model.Paging;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.apache.poi.hssf.record.RecordFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author zhuangyuhao
 * @Date 2017/9/18 10:48
 */
@RestController
@RequestMapping("boss/robot")
public class RobotController {

    @Autowired
    private RobotFacade robotFacade;

    @ApiOperation(value = "创建机器人", notes = "创建机器人", response = Response.class)
    @RequestMapping(value = "/create_robot", method = RequestMethod.POST)
    public Response createRobot(@ApiParam(value = "需要创建的机器人数量") @RequestParam Integer num) throws IOException {
        Response response = new Response();
        robotFacade.batchAddRobotUser(num);
        return response;
    }

    @ApiOperation(value = "对指定的帖子进行指定机器人的点赞操作", notes = "对指定的帖子进行指定机器人的点赞操作", response = Response.class)
    @RequestMapping(value = "/robot_zan_post", method = RequestMethod.POST)
    public Response robotZanPost(@ApiParam(value = "需要调用的机器人数量") @RequestParam Integer num,
                                 @ApiParam(value = "帖子id") @RequestParam Integer postid) throws IOException {
        Response response = new Response();
        robotFacade.robotZanPost(postid, num);
        return response;
    }

    @ApiOperation(value = "机器人收藏帖子操作", notes = "机器人收藏帖子操作", response = Response.class)
    @RequestMapping(value = "/robot_collect_post", method = RequestMethod.POST)
    public Response robotCollectPost(@ApiParam(value = "需要调用的机器人数量") @RequestParam Integer num,
                                     @ApiParam(value = "帖子id") @RequestParam Integer postid) throws IOException {
        Response response = new Response();
        robotFacade.robotCollectPost(postid, num);
        return response;
    }

    @ApiOperation(value = "机器人关注用户操作", notes = "机器人关注用户操作", response = Response.class)
    @RequestMapping(value = "/robot_follow_user", method = RequestMethod.POST)
    public Response robotFollowUser(@ApiParam(value = "需要调用的机器人数量") @RequestParam Integer num,
                                    @ApiParam(value = "被关注的用户id") @RequestParam Integer userid) throws IOException {
        Response response = new Response();
        robotFacade.robotFollowUser(userid, num);
        return response;
    }

    /**
     * 查询机器人列表
     *
     * @param name
     * @param pageNo
     * @param pageSize
     * @return
     */
    @ApiOperation(value = "机器人列表", notes = "用于查询机器人列表接口", response = Response.class)
    @RequestMapping(value = "/robot_query_all", method = RequestMethod.POST)
    public Response QueryRobotByList(@ApiParam(value = "机器人名称") @RequestParam(required = false) String name,
                                     @ApiParam(value = "当前页") @RequestParam(defaultValue = "1") String pageNo,
                                     @ApiParam(value = "每页几条") @RequestParam(defaultValue = "10") String pageSize) {
        Response response = new Response();
        List<User> userVoList = new ArrayList<User>();
        Paging<User> pag = new Paging<User>(Integer.valueOf(pageNo), Integer.valueOf(pageSize));
        if (pageNo != null && pageSize != null) {
            //查询机器人列表,用于列表查询
            userVoList = robotFacade.QueryRobotByList(name, pag);
        } else {
            //查询机器人，用于选择
            userVoList = robotFacade.QueryRobotByList();
        }
        pag.result(userVoList);
        response.setData(pag);
        response.setMessage("查询成功");
        return response;
    }


    /**
     * 查询机器人详情
     *
     * @param id
     * @return
     */
    @ApiOperation(value = "查询机器人", notes = "用于查询机器人详情接口", response = Response.class)
    @RequestMapping(value = "/robot_query_id", method = RequestMethod.POST)
    public Response queryRobotById(@ApiParam(value = "id") @RequestParam String id) {
        Response response = new Response();
        User user = robotFacade.queryRobotById(id);
        response.setMessage("查询成功");
        response.setData(user);
        return response;
    }

    /**
     * 更新机器人信息
     *
     * @param id
     * @param email
     * @param nickname
     * @param phone
     * @param photo
     * @param sex
     * @return
     */
    @ApiOperation(value = "更新机器人", notes = "更新机器人", response = Response.class)
    @RequestMapping(value = "/robot_update_id", method = RequestMethod.POST)
    public Response updateRoboltById(@ApiParam(value = "id") @RequestParam(required = false) String id,
                                     @ApiParam(value = "邮箱") @RequestParam(required = false) String email,
                                     @ApiParam(value = "nickname") @RequestParam(required = false) String nickname,
                                     @ApiParam(value = "手机号") @RequestParam(required = false) String phone,
                                     @ApiParam(value = "头像") @RequestParam(required = false) String photo,
                                     @ApiParam(value = "性别") @RequestParam(required = false) String sex) {
        Response response = new Response();
        robotFacade.updateRoboltById(id, email, nickname, phone, photo, sex);
        response.setMessage("操作成功");
        response.setData(1);
        return response;
    }


}