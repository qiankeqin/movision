package com.movision.facade.im;

import com.google.gson.Gson;
import com.movision.common.Response;
import com.movision.common.constant.ImConstant;
import com.movision.common.constant.MsgCodeConstant;
import com.movision.common.util.ShiroUtil;
import com.movision.exception.BusinessException;
import com.movision.fsearch.utils.StringUtil;
import com.movision.mybatis.imDevice.entity.ImDevice;
import com.movision.mybatis.imDevice.service.ImDeviceService;
import com.movision.mybatis.imFirstDialogue.entity.ImFirstDialogue;
import com.movision.mybatis.imFirstDialogue.entity.ImMsg;
import com.movision.mybatis.imFirstDialogue.service.ImFirstDialogueService;
import com.movision.mybatis.imSystemInform.entity.ImBatchAttachMsg;
import com.movision.mybatis.imSystemInform.entity.ImSystemInform;
import com.movision.mybatis.imSystemInform.entity.ImSystemInformVo;
import com.movision.mybatis.imSystemInform.service.ImSystemInformService;
import com.movision.mybatis.imuser.entity.ImUser;
import com.movision.mybatis.imuser.service.ImUserService;
import com.movision.mybatis.post.service.PostService;
import com.movision.mybatis.systemPush.entity.SystemPush;
import com.movision.mybatis.systemPush.service.SystemPushService;
import com.movision.mybatis.systemToPush.entity.SystemToPush;
import com.movision.mybatis.systemToPush.service.SystemToPushService;
import com.movision.utils.JsonUtils;
import com.movision.utils.JsoupCompressImg;
import com.movision.utils.ListUtil;
import com.movision.utils.SignUtil;
import com.movision.utils.convert.BeanUtil;
import com.movision.utils.im.CheckSumBuilder;
import com.movision.utils.pagination.model.Paging;
import com.movision.utils.propertiesLoader.PropertiesLoader;
import com.movision.utils.sms.SDKSendSms;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;

/**
 * 网易云通讯接口
 *
 * @Author zhuangyuhao
 * @Date 2017/3/6 17:07
 */
@Service
public class ImFacade {

    private static Logger log = LoggerFactory.getLogger(ImFacade.class);

    @Autowired
    private PostService postService;

    @Autowired
    private ImUserService imUserService;

    @Autowired
    private SystemPushService systemPushService;

    @Autowired
    private ImFirstDialogueService imFirstDialogueService;

    @Autowired
    private ImSystemInformService imSystemInformService;
    @Autowired
    private SystemToPushService systemToPushService;

    @Autowired
    private ImDeviceService imDeviceService;

    @Autowired
    private JsoupCompressImg jsoupCompressImg;

    /**
     * 发起IM请求，获得响应
     *
     * @param url    向IM服务器发送的请求的url
     * @param params 接口的传参
     * @return
     * @throws IOException
     */
    public Map<String, Object> sendImHttpPost(String url, Map<String, Object> params) throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {

            HttpPost httpPost = new HttpPost(url);
            log.info("请求的云信url:" + url);

            String appKey = ImConstant.APP_KEY;
            String appSecret = ImConstant.APP_SECRET;
            String nonce = SignUtil.generateString(32);
            String curTime = String.valueOf((new Date()).getTime() / 1000L);
            String checkSum = CheckSumBuilder.getCheckSum(appSecret, nonce, curTime);//参考 计算CheckSum的java代码

            // 设置请求的header
            httpPost.addHeader("AppKey", appKey);
            httpPost.addHeader("Nonce", nonce);
            httpPost.addHeader("CheckSum", checkSum);
            httpPost.addHeader("CurTime", curTime);
            httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");

            // 设置请求的参数
            List<NameValuePair> nvps = new ArrayList<NameValuePair>();
            List<String> keyList = new ArrayList<String>(params.keySet());
            for (String key : keyList) {
                nvps.add(new BasicNameValuePair(key, String.valueOf(params.get(key))));
            }
            log.info("请求云信接口的传参params：" + params);
            httpPost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

            // 执行请求
            CloseableHttpResponse response = httpclient.execute(httpPost);

            // 打印执行结果
            // {"code":200,"info":{"token":"a967478ef49bd18cfaa369dec8b6a74f","accid":"test_create_user","name":""}}
//        System.out.println(EntityUtils.toString(response.getEntity(), "utf-8"));
            try {
                HttpEntity entity = response.getEntity();
                // do something useful with the response body
                // and ensure it is fully consumed
                String result = EntityUtils.toString(entity, "utf-8");
                Map<String, Object> resultMap = JsonUtils.getObjectMapFromJsonString(result);
//                log.info("转换成map的结果："+resultMap);
                EntityUtils.consume(entity);
                log.info("返回的结果：" + resultMap);
                return resultMap;
            } finally {
                response.close();
            }

        } catch (ConnectTimeoutException e) {
            log.error("请求连接超时：" + e.getMessage());
            e.printStackTrace();
        } catch (SocketTimeoutException e) {
            log.error("返回超时：" + e.getMessage());
            e.printStackTrace();
        } finally {
            httpclient.close();
        }
        return null;
    }


    /**
     * 向Im服务器注册IM用户
     *
     * @throws IOException
     */
    public Map<String, Object> registerIM(ImUser imUser) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("accid", imUser.getAccid());

        if (StringUtils.isNotEmpty(imUser.getName())) {
            params.put("name", imUser.getName());
        }

        if (StringUtils.isNotEmpty(imUser.getIcon())) {
            params.put("icon", imUser.getIcon());
        }

        if (StringUtils.isNotEmpty(imUser.getToken())) {
            params.put("token", imUser.getToken());
        }

        return this.sendImHttpPost(ImConstant.CREATE_USER_URL, params);
    }

    public Map<String, Object> registerIMDevice(ImDevice imDevice) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("accid", imDevice.getAccid());

        if (StringUtils.isNotEmpty(imDevice.getName())) {
            params.put("name", imDevice.getName());
        }

        if (StringUtils.isNotEmpty(imDevice.getIcon())) {
            params.put("icon", imDevice.getIcon());
        }

        if (StringUtils.isNotEmpty(imDevice.getToken())) {
            params.put("token", imDevice.getToken());
        }

        return this.sendImHttpPost(ImConstant.CREATE_USER_URL, params);
    }

    /**
     * APP新增IM用户
     *
     * @param imUser
     * @return 把返回值给app端
     * @throws IOException
     */
    public ImUser AddImUser(ImUser imUser) throws IOException {

        this.registerImUserAndSave(imUser, imUser.getUserid(), ImConstant.TYPE_APP);

        return imUserService.selectByUserid(imUser.getUserid(), ImConstant.TYPE_APP);
    }

    /**
     * 注册云信用户， 并且保存用户信息在本地
     *
     * @param imUser
     * @param currentUserid 注册IM账号对应的userid
     * @param systemType    系统类型，1：APP， 0：BOSS
     * @throws IOException
     */
    public void registerImUserAndSave(ImUser imUser, int currentUserid, int systemType) throws IOException {
        //注册im用户
        Map res = this.registerIM(imUser);

        if (res.get("code").equals(200)) {

            String info = JsonUtils.getJsonStringFromObj(res.get("info"));
            Map infoMap = JsonUtils.getObjectMapFromJsonString(info);
            String token = String.valueOf(infoMap.get("token"));
            String accid = String.valueOf(infoMap.get("accid"));
            String name = String.valueOf(infoMap.get("name"));

            log.info("注册IM用户从服务器返回的值：token=" + token + ",accid=" + accid + ",name=" + name);

            //im用户信息入库
            ImUser finalImUser = new ImUser();
            finalImUser.setAccid(accid);
            finalImUser.setToken(token);
            finalImUser.setName(name);
            finalImUser.setUserid(currentUserid);
            finalImUser.setType(systemType);
            imUserService.addImUser(finalImUser);

        } else {
            throw new BusinessException(MsgCodeConstant.create_im_accid_fail, "注册云信用户失败");
        }
    }


    public void registerImDeviceAndSave(ImDevice imDevice) throws IOException {
        //注册im用户
        Map res = this.registerIMDevice(imDevice);

        if (res.get("code").equals(200)) {
            //返回值
            String info = JsonUtils.getJsonStringFromObj(res.get("info"));
            Map infoMap = JsonUtils.getObjectMapFromJsonString(info);
            String token = String.valueOf(infoMap.get("token"));
            String accid = String.valueOf(infoMap.get("accid"));
            String name = String.valueOf(infoMap.get("name"));

            log.info("注册IM用户从服务器返回的值：token=" + token + ",accid=" + accid + ",name=" + name);

            //im用户信息入库
            ImDevice finalImUser = new ImDevice();
            finalImUser.setAccid(accid);
            finalImUser.setToken(token);
            finalImUser.setName(name);
            finalImUser.setDeviceid(imDevice.getDeviceid());
            imDeviceService.add(finalImUser);

        } else {
            throw new BusinessException(MsgCodeConstant.create_im_accid_fail, "根据设备号注册云信用户失败");
        }
    }


    /**
     * 根据userid查找当前APP用户的IM信息
     *
     * @param userid
     * @return
     */
    public ImUser getImuserByCurrentAppuser(int userid) {
        return imUserService.selectByUserid(userid, ImConstant.TYPE_APP);
    }


    /**
     * 获取当前boss用户的IM用户信息
     *
     * @return
     */
    public ImUser getImuserByCurrentBossuser() {
        return imUserService.selectByUserid(ShiroUtil.getBossUserID(), ImConstant.TYPE_BOSS);
    }

    public ImUser getImuser(Integer uid, Integer type) {
        return imUserService.selectByUserid(uid, type);
    }

    /**
     * 判断是否存在APP IM账号
     *
     * @return true:存在；  false:不存在
     */
    public Boolean isExistAPPImuser(int userid) {
        ImUser imUser = imUserService.selectByUserid(userid, ImConstant.TYPE_APP);
        return null != imUser;
    }

    /**
     * 判断是否存在BOSS IM账号
     *
     * @return
     */
    public Boolean isExistBossImuser() {
        ImUser imUser = imUserService.selectByUserid(ShiroUtil.getBossUserID(), ImConstant.TYPE_BOSS);
        return null != imUser;
    }

    /**
     * 更新IM用户名片
     *
     * @param map accid 必填
     *            name，icon， sign， email， birth， mobile， gender， ex
     * @return {
     * "code":200
     * }
     * @throws IOException
     */
    public Map updateImUserInfo(Map map) throws IOException {
        return this.sendImHttpPost(ImConstant.UPDATE_USER_INFO_URL, map);
    }


    /**
     * 查询IM名片
     *
     * @param accids 对应的accid串，如：["zhangsan"]，一次最多200个
     * @return
     * @throws IOException
     */
    public Map queryImuserInfo(String[] accids) throws IOException {

        Map<String, Object> params = new HashMap<>();
        //需要把入参以字符串数组的形式，转化成json字符串
        Gson gson = new Gson();
        String str = gson.toJson(accids);
        params.put("accids", str);
        return this.sendImHttpPost(ImConstant.GET_USER_INFO, params);
    }


    /**
     * 加好友
     *
     * @param accid  加好友发起者accid
     * @param faccid 加好友接收者accid
     * @param type   1直接加好友，2请求加好友，3同意加好友，4拒绝加好友
     * @param msg    加好友对应的请求消息，第三方组装，最长256字符
     * @return
     * @throws IOException
     */
    public Map addFriend(String accid, String faccid, int type, String msg) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("accid", accid);
        params.put("faccid", faccid);
        params.put("type", type);
        if (StringUtils.isNotEmpty(msg)) {
            params.put("msg", msg);
        }
        return this.sendImHttpPost(ImConstant.ADD_FRIEND, params);
    }

    /**
     * 增加一条加好友的记录
     *
     * @param toAccid
     * @param msg
     * @return
     */
    public int addFirstDialogue(String toAccid, String msg) {
        ImFirstDialogue imFirstDialogue = new ImFirstDialogue();
        imFirstDialogue.setUserid(ShiroUtil.getAppUserID());
        imFirstDialogue.setFromid(ShiroUtil.getAccid());
        imFirstDialogue.setToid(toAccid);
        imFirstDialogue.setBody(msg);

        return imFirstDialogueService.addDialogue(imFirstDialogue);
    }


    /**
     * 发消息接口
     *
     * @param imMsg
     * @return
     * @throws IOException
     */
    public Map sendMsg(ImMsg imMsg) throws IOException {

        Map<String, Object> params = BeanUtil.ImBeanToMap(imMsg);

        return this.sendImHttpPost(ImConstant.SEND_MSG, params);
    }

    /**
     * 判断是否存在该次对话记录
     *
     * @param toAccid
     * @return
     */
    public Boolean isExistFirstDialog(String toAccid) {
        ImFirstDialogue imFirstDialogue = new ImFirstDialogue();
        imFirstDialogue.setFromid(ShiroUtil.getAccid());
        imFirstDialogue.setToid(toAccid);

        int result = imFirstDialogueService.isExistFirstDialogue(imFirstDialogue);
        return result == 1;
    }

    /**
     * 判断被我打招呼的人是否回复
     *
     * @param replyAccid
     * @return
     */
    public Boolean isExistReply(String replyAccid) {
        ImFirstDialogue imFirstDialogue = new ImFirstDialogue();
        imFirstDialogue.setFromid(replyAccid);
        imFirstDialogue.setToid(ShiroUtil.getAccid());

        int result = imFirstDialogueService.isExistReply(imFirstDialogue);
        return result == 1;
    }

    /**
     * 查询我和对方的对话记录
     *
     * @param toAccid
     * @return
     */
    public List<ImFirstDialogue> selectFirstDialog(String toAccid) {
        ImFirstDialogue imFirstDialogue = new ImFirstDialogue();
        imFirstDialogue.setFromid(ShiroUtil.getAccid());
        imFirstDialogue.setToid(toAccid);

        return imFirstDialogueService.selectFirstDialog(imFirstDialogue);
    }

    /**
     * @param imMsg
     * @param addFriendType 2:请求加好友   3：接受加好友
     * @param responseMsg
     * @return
     * @throws IOException
     */
    public Response doFirstCommunicate(ImMsg imMsg, int addFriendType, String responseMsg) throws IOException {
        Response response = new Response();
        //1 发消息
        Map sendMsgResult = this.sendMsg(imMsg);
        Object code_1 = sendMsgResult.get("code");
        /**
         *  addFriendType=2 请求加好友
         *  addFriendType=3 接受加好友
         */
        Map map = this.addFriend(ShiroUtil.getAccid(), imMsg.getTo(), addFriendType, imMsg.getBody());
        Object code_2 = map.get("code");

        if (code_2.equals(200) && code_1.equals(200)) {
            response.setCode(200);
            response.setMessage(responseMsg + "成功");
            //3 记录发送的消息
            this.addFirstDialogue(imMsg.getTo(), imMsg.getBody());
        } else {
            response.setCode(400);
            response.setMessage(responseMsg + "失败");
        }

        return response;
    }

    /**
     * 发送系统通知并记录
     *
     * @param body
     * @param title
     * @param pushcontent
     * @throws IOException
     */
    public void sendSystemInform(String body, String title, String pushcontent, Integer type, String coverimg) throws IOException {
        Date date = new Date();
        long informidentity = date.getTime();
        //获取当前boss用户的IM用户信息
        ImUser imUser = this.getImuserByCurrentBossuser();
        //找到所有的im用户
        List<ImUser> imAppUserList = imUserService.selectAllAPPImuser();

        if (ListUtil.isNotEmpty(imAppUserList)) {
            int size = imAppUserList.size();
            log.info("app中的IM用户共" + size + "人！");
            if (size > 500) {
                //人数多于500人，分批次发系统通知
                int mutiple = size / 500;   //倍数
                for (int i = 0; i <= mutiple; i++) {
                    /**
                     * 比如共1002人，
                     * 那么i=0, 即第0-500人， 取500人
                     *     i=1, 即第501-1000人，    取500人
                     *     i=2, 即第1001-1002人，   取两人
                     */
                    int eachSize = i < mutiple ? 500 : size - mutiple * 500;
                    sendAndRecord(body, imUser, imAppUserList, eachSize, i, title, pushcontent, informidentity, type, coverimg);
                }
            } else {
                //不超过500人
                sendAndRecord(body, imUser, imAppUserList, size, 0, title, pushcontent, informidentity, type, coverimg);
            }
        }
    }

    public void addOperationInform(HttpServletRequest request, String body, String title, String coverimg) {
        try {
            //生成运营推送的body内容
            //String str = makePushBody(request, body);
            //推送业务
            sendSystemInform(body, title, title, ImConstant.PUSH_MESSAGE.operation_msg.getCode(), coverimg);

        } catch (IOException e) {
            log.error("发送运营通知失败", e);
            throw new BusinessException(MsgCodeConstant.SYSTEM_ERROR, "发送运营通知失败");
        }
    }

    /**
     * 生成运营推送的body内容
     *
     * @param request
     * @param body
     * @return
     */
    private String makePushBody(HttpServletRequest request, String body) {
        String str = "";
        //内容转换
        Map con = jsoupCompressImg.compressImg(request, body);
        log.debug("压缩后的内容con:" + con);
        if ((int) con.get("code") == 200) {
            str = con.get("content").toString();
            str = str.replace("\\", "");
            log.debug(str);
        } else {
            log.error("内容转换异常");
            throw new BusinessException(MsgCodeConstant.SYSTEM_ERROR, "内容转换异常");
        }
        return str;
    }

    /**
     * 条件查询运营通知列表
     *
     * @param title
     * @param body
     * @param pag
     * @return
     */
    public List<ImSystemInform> queryOperationInformList(String title, String body, Paging<ImSystemInform> pag) {
        ImSystemInform inform = new ImSystemInform();
        if (StringUtil.isNotEmpty(title)) {
            inform.setTitle(title);
        }
        if (StringUtil.isNotEmpty(body)) {
            inform.setBody(body);
        }
        return imSystemInformService.queryOperationInformList(inform, pag);
    }

    /**
     * 查询运营通知详情
     *
     * @param id
     * @return
     */
    public ImSystemInform queryOperationInformById(String id) {
        ImSystemInform inform = new ImSystemInform();
        inform.setId(Integer.parseInt(id));
        return imSystemInformService.queryOperationInformById(inform);
    }

    /**
     * 更新运营通知
     *
     * @param id
     * @param title
     * @param body
     * @param coverimg
     */
    public void updateOperationInformById(String id, String title, String body, String coverimg) {
        ImSystemInform inform = new ImSystemInform();
        inform.setId(Integer.parseInt(id));
        if (StringUtil.isNotEmpty(title)) {
            inform.setTitle(title);
        }
        if (StringUtil.isNotEmpty(body)) {
            inform.setBody(body);
        }
        if (StringUtil.isNotEmpty(coverimg)) {
            inform.setCoverimg(coverimg);
        }
        imSystemInformService.updateOperationInformById(inform);
    }


    /**
     * 准备toAccids参数,发送系统通知，并且记录
     *
     * @param body
     * @param imUser
     * @param imAppUserList
     * @param size
     * @param multiple
     * @param title
     * @param pushcontent
     * @param informidentity
     * @param type           推送类型
     * @param coverimg       运营通知的封面图
     * @throws IOException
     */
    private void sendAndRecord(String body, ImUser imUser, List<ImUser> imAppUserList, int size, int multiple, String title,
                               String pushcontent, long informidentity, Integer type, String coverimg) throws IOException {
        //接受者
        String toAccids = prepareToAccids(imAppUserList, size, multiple);
        //记录流水(运营消息/推送/系统消息/推送)
        Integer pushid = this.recordSysInforms(body, imUser.getAccid(), toAccids, title, pushcontent, informidentity, coverimg);
        //封装payload
        String payload = wrapPushcontent(type, pushcontent, pushid, coverimg, body);
        log.debug("封装的 payload:" + payload);
        //调用云信接口发送通知
        Map result = this.sendSystemInform(body, imUser.getAccid(), toAccids, pushcontent, payload);

        if (result.get("code").equals(200)) {
            log.info("发送系统通知成功，发送人accid=" + imUser.getAccid() + ",接收人accids=" + toAccids + ",发送内容=" + body);
        } else {
            log.error("发送系统通知失败，发送人accid=" + imUser.getAccid() + ",接收人accids=" + toAccids + ",发送内容=" + body);
            throw new BusinessException(MsgCodeConstant.send_system_msg_fail, "发送系统通知失败");
        }
        //更新推送信息记录
        updatePushInfo(pushcontent, pushid, payload);
    }

    /**
     * 更新推送信息
     *
     * @param pushcontent
     * @param pushid
     * @param pushStr
     */
    private void updatePushInfo(String pushcontent, int pushid, String pushStr) {
        if (StringUtils.isNotBlank(pushcontent)) {

            SystemToPush systemToPush = new SystemToPush();
            systemToPush.setId(pushid);
            systemToPush.setBody(pushStr);
            systemToPushService.updateBySelective(systemToPush);
        }
    }

    /**
     * 封装pushcontent
     *
     * @param pushcontent
     * @param pushid
     * @return
     */
    private String wrapPushcontent(Integer type, String pushcontent, Integer pushid, String img, String body) {
        Map map = new HashMap();
        map.put("type", ImConstant.PUSH_MESSAGE.system_msg.getCode());
        map.put("id", pushid);
        map.put("msg", pushcontent);
        map.put("img", img);
        map.put("body", body);

        Gson gson = new Gson();
        return gson.toJson(map);
    }

    /**
     * 准备toAccids参数
     *
     * @param imAppUserList
     * @param size
     * @param multiple      倍数，0,1,2,。。。
     * @return
     */
    private String prepareToAccids(List<ImUser> imAppUserList, int size, int multiple) {
        String[] str = new String[size];
        for (int i = 0; i < size; i++) {
            str[i] = imAppUserList.get(i + 500 * multiple).getAccid();
        }
        Gson gson = new Gson();
        return gson.toJson(str);
    }

    /**
     * 发送系统通知
     *
     * @param fromaccid
     * @param body
     * @param toAccids
     * @return
     * @throws IOException
     */
    public Map sendSystemInform(String body, String fromaccid, String toAccids, String pushcontent, String payload) throws IOException {
        //发系统通知
        Gson gson = new Gson();
        Map map = new HashMap();
        map.put("badge", false);
        map.put("needPushNick", false);
        map.put("route", false);
        String option = gson.toJson(map);
        ImBatchAttachMsg imBatchAttachMsg = new ImBatchAttachMsg();
        imBatchAttachMsg.setFromAccid(fromaccid);
        imBatchAttachMsg.setAttach(body);
        //推送则封装pushcontent
        if (!StringUtils.isEmpty(pushcontent)) {
            imBatchAttachMsg.setPushcontent(pushcontent);
        }
        imBatchAttachMsg.setPayload(payload);
        imBatchAttachMsg.setToAccids(toAccids);
        imBatchAttachMsg.setOption(option);
        return this.sendImHttpPost(ImConstant.SEND_BATCH_ATTACH_MSG, BeanUtil.ImBeanToMap(imBatchAttachMsg));
    }

    public Map sendSystemInformTo(String body, String fromaccid, String toAccids) throws IOException {
        //发系统通知
        Gson gson = new Gson();
        Map map = new HashMap();
        map.put("badge", false);
        map.put("needPushNick", false);
        map.put("route", false);
        String option = gson.toJson(map);
        ImBatchAttachMsg imBatchAttachMsg = new ImBatchAttachMsg();
        imBatchAttachMsg.setFromAccid(fromaccid);
        imBatchAttachMsg.setAttach(body);
        imBatchAttachMsg.setToAccids(toAccids);
        imBatchAttachMsg.setOption(option);
        return this.sendImHttpPost(ImConstant.SEND_BATCH_ATTACH_MSG, BeanUtil.ImBeanToMap(imBatchAttachMsg));
    }

    /**
     * 发送系统通知
     * 打赏  评论  点赞
     *
     * @param fromaccid
     * @param body
     * @param to
     * @param pushcontent 不为空，则是手机推送
     * @return
     * @throws IOException
     */
    public Map sendMsgInform(String body, String fromaccid, String to, String pushcontent) throws IOException {
        //发系统通知打赏评论点赞
        ImBatchAttachMsg imBatchAttachMsg = new ImBatchAttachMsg();
        imBatchAttachMsg.setFromAccid(fromaccid);
        imBatchAttachMsg.setAttach(body);
        imBatchAttachMsg.setPushcontent(pushcontent);
        imBatchAttachMsg.setTo(to);
        imBatchAttachMsg.setMsgtype(0);
        return this.sendImHttpPost(ImConstant.SEND_MSG_BATCH, BeanUtil.ImBeanToMap(imBatchAttachMsg));
    }


    /**
     * 记录通知+推送流水
     *
     * @param body
     * @param fromaccid
     * @param toAccids
     * @param title
     * @param pushcontent
     * @param informidentity
     * @param coverimg
     * @return
     */
    public Integer recordSysInforms(String body, String fromaccid, String toAccids, String title, String pushcontent,
                                    long informidentity, String coverimg) {
        //系统通知表:普通通知
        addImSystemInform(body, fromaccid, toAccids, title, informidentity, coverimg);
        //推送表(记录系统推送)
        return addSystemPushInfo(body, fromaccid, toAccids, title);
    }

    private Integer addSystemPushInfo(String body, String fromaccid, String toAccids, String title) {
        SystemToPush systemToPush = new SystemToPush();
        systemToPush.setBody(body);
        systemToPush.setTitle(title);
        systemToPush.setFromAccid(fromaccid);
        systemToPush.setToAccids(toAccids);
        systemToPush.setUserid(ShiroUtil.getBossUserID());
        systemToPush.setInformTime(new Date());
        return systemToPushService.addSystemToPush(systemToPush);
    }

    private void addImSystemInform(String body, String fromaccid, String toAccids, String title,
                                   long informidentity, String coverimg) {
        ImSystemInform imSystemInform = new ImSystemInform();
        imSystemInform.setBody(body);
        imSystemInform.setFromAccid(fromaccid);
        imSystemInform.setUserid(ShiroUtil.getBossUserID());
        imSystemInform.setToAccids(toAccids);
        imSystemInform.setTitle(title);
        imSystemInform.setInformTime(new Date());
        imSystemInform.setInformidentity(String.valueOf(informidentity));
        imSystemInform.setCoverimg(coverimg);
        imSystemInformService.add(imSystemInform);
    }

    /**
     * 新增系统通知记录
     *
     * @param body
     * @param coverimg
     * @param fromaccid
     * @param title
     * @param informidentity
     */
    private void recordSysInformsTo(String body, String coverimg, String fromaccid, String toaccids,
                                    String title, long informidentity) {
        ImSystemInform imSystemInform = new ImSystemInform();
        imSystemInform.setBody(body);
        imSystemInform.setCoverimg(coverimg);
        imSystemInform.setFromAccid(fromaccid);
        imSystemInform.setToAccids(toaccids);
        imSystemInform.setUserid(ShiroUtil.getBossUserID());
        imSystemInform.setTitle(title);
        imSystemInform.setInformTime(new Date());
        imSystemInform.setInformidentity(String.valueOf(informidentity));
        imSystemInformService.add(imSystemInform);
    }

    /**
     * 活动通知
     *
     * @param body
     * @param fromaccid
     * @param
     * @param informidentity
     */
    public void activeMessage(String body, String fromaccid, long informidentity, String toAccids, String title, int activeid) {
        ImSystemInform imSystemInform = new ImSystemInform();
        imSystemInform.setBody(body);
        imSystemInform.setFromAccid(fromaccid);
        imSystemInform.setUserid(ShiroUtil.getBossUserID());
        imSystemInform.setInformTime(new Date());
        imSystemInform.setInformidentity(String.valueOf(informidentity));
        imSystemInform.setToAccids(toAccids);
        imSystemInform.setTitle(title);
        imSystemInform.setActiveid(activeid);
        //每次取500个人
        imSystemInformService.add(imSystemInform);
    }


    /**
     * 查询所有的系统通知
     *
     * @param paging
     * @return
     */
    public List<ImSystemInformVo> queryAllSystemInform(Paging<ImSystemInformVo> paging) {

        return imSystemInformService.queryAll(paging);
    }

    public ImSystemInform querySystemInformDetail(Integer id) {
        return imSystemInformService.queryDetail(id);
    }

    /**
     * 删除系统通知
     *
     * @param id
     * @return
     */
    public Integer deleteImSystem(Integer id) {
        return imSystemInformService.deleteImSystem(id);
    }

    /**
     * 条件搜索
     *
     * @param body
     * @param pai
     * @param pager
     * @return
     */
    public List<ImSystemInform> findAllSystemCondition(String body, String pai, Paging<ImSystemInform> pager) {
        Map<String, Object> map = new HashMap<>();
        if (body != null) {
            map.put("body", body);
        }
        if (pai != null) {
            map.put("pai", pai);
        }
        return imSystemInformService.findAllSystemForm(map, pager);
    }

    /**
     * 查询内容全部
     *
     * @param id
     * @return
     */
    public ImSystemInform queryBodyAll(Integer id) {
        return imSystemInformService.queryBodyAll(id);
    }

    /**
     * 查询消息列表
     *
     * @param pager
     * @return
     */
    public List<SystemPush> findAllSyetemPush(Paging<SystemPush> pager) {
        return systemPushService.findAllSystemPush(pager);
    }

    /**
     * 消息搜索
     *
     * @param body
     * @param pai
     * @param pager
     * @return
     */
    public List<SystemPush> findAllPushCondition(String body, String pai, Paging<SystemPush> pager) {
        Map<String, Object> map = new HashMap<>();
        if (body != null) {
            map.put("body", body);
        }
        if (pai != null) {
            map.put("pai", pai);
        }
        return systemPushService.findAllPushCondition(map, pager);
    }

    /**
     * 查询消息内容
     *
     * @param id
     * @return
     */
    public SystemPush queryPushBody(Integer id) {
        return systemPushService.queryPushBody(id);
    }

    /**
     * 删除消息
     *
     * @param id
     * @return
     */
    public Integer deleteSystemPush(Integer id) {
        return systemPushService.deleteSystemPush(id);
    }

    /**
     * 消息推送(不带链接的消息推送)
     *
     * @param
     * @param
     * @param
     * @param body
     * @return
     */
    public void AddPushMovement(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        SystemPush systemPush = new SystemPush();
        systemPush.setUserid(ShiroUtil.getBossUserID());
        systemPush.setBody(body);
        systemPush.setInformTime(new Date());
        int result = systemPushService.addPush(systemPush);
        List<String> list = systemPushService.findAllPhone();
        int pageNo = 1;
        int pageSize = 200;
        if (list.size() <= 200) {
            for (int i = 0; i < list.size(); i++) {
                String mobile = "";
                mobile += list.get(i) + ",";
                mobile = mobile.substring(0, mobile.length() - 1);
                map.put("body", body);
                Gson gson = new Gson();
                String json = gson.toJson(map);
                SDKSendSms.sendSMS(mobile, json, PropertiesLoader.getValue("propelling_movement_infomation"));
            }
        }
        int totalPageNum = (list.size() + pageSize - 1) / pageSize;
        if (list.size() > 200) {
            for (int j = 0; j <= totalPageNum; j++) {
                Paging pa = new Paging(Integer.valueOf(pageNo), Integer.valueOf(pageSize));
                List<String> phone = systemPushService.findPhone(pa);
                String mobile = "";
                for (int i = 0; i < phone.size(); i++) {
                    mobile += phone.get(i) + ",";
                }
                mobile = mobile.substring(0, mobile.length() - 1);
                pageNo += 1;
                map.put("body", body);
                Gson gson = new Gson();
                String json = gson.toJson(map);
                SDKSendSms.sendSMS(mobile, json, PropertiesLoader.getValue("propelling_movement_infomation"));
            }

        }
    }


    /**
     * 消息推送(带链接的消息推送)
     *
     * @param
     * @param
     * @param
     * @param body
     * @return
     */
    public void AddPushMovementAndLink(String body, String code) {
        Map<String, String> map = new LinkedHashMap<>();
        SystemPush systemPush = new SystemPush();
        systemPush.setUserid(ShiroUtil.getBossUserID());
        systemPush.setBody(body);
        systemPush.setInformTime(new Date());
        int result = systemPushService.addPush(systemPush);
        List<String> list = systemPushService.findAllPhone();
        int pageNo = 1;
        int pageSize = 200;
        if (list.size() <= 200) {
            for (int i = 0; i < list.size(); i++) {
                String mobile = "";
                mobile += list.get(i) + ",";
                mobile = mobile.substring(0, mobile.length() - 1);
                map.put("body", body);
                map.put("code", code);
                Gson gson = new Gson();
                String json = gson.toJson(map);
                SDKSendSms.sendSMS(mobile, json, PropertiesLoader.getValue("propelling_movement_infomation_link"));
            }
        }
        int totalPageNum = (list.size() + pageSize - 1) / pageSize;
        if (list.size() > 200) {
            for (int j = 0; j <= totalPageNum; j++) {
                Paging pa = new Paging(Integer.valueOf(pageNo), Integer.valueOf(pageSize));
                List<String> phone = systemPushService.findPhone(pa);
                String mobile = "";
                for (int i = 0; i < phone.size(); i++) {
                    mobile += phone.get(i) + ",";
                }
                mobile = mobile.substring(0, mobile.length() - 1);
                pageNo += 1;
                map.put("body", body);
                map.put("code", code);
                Gson gson = new Gson();
                String json = gson.toJson(map);
                SDKSendSms.sendSMS(mobile, json, PropertiesLoader.getValue("propelling_movement_infomation_link"));
            }

        }
    }


    public List<SystemToPush> findAllSystemToPush(Paging<SystemToPush> pager) {
        return systemToPushService.findAllSystemToPush(pager);
    }

    public List<SystemToPush> findAllSystenToPushCondition(String body, String pai, Paging<SystemToPush> paging) {
        Map<String, Object> map = new HashMap();
        if (!StringUtils.isEmpty(body)) {
            map.put("body", body);
        }
        if (!StringUtils.isEmpty(pai)) {
            map.put("pai", pai);
        }
        return systemToPushService.findAllSystenToPushCondition(map, paging);
    }

    public SystemToPush querySystemToPushBody(Integer id) {
        return systemToPushService.querySystemToPushBody(id);
    }

    public Integer deleteSystemToPush(Integer id) {
        return systemToPushService.deleteSystemToPush(id);
    }

    /**
     *
     *
     * 系统推送
     *
     * @param
     * @return
     */
    /*public void addSystemToPush(String body, String title) {
        SystemToPush systemToPush = new SystemToPush();
        systemToPush.setBody(body);
        systemToPush.setTitle(title);
        systemToPush.setUserid(ShiroUtil.getBossUserID());
        systemToPush.setInformTime(new Date());
        systemToPushService.addSystemToPush(systemToPush);//记录流水
    }*/

    /**
     * 系统推送
     *
     * @param body
     * @param title
     */
    /*public void systemPushMessage(String body, String title, JSONObject jsonObjectPayload, int deviceType) throws Exception {
        //安卓这边是使用小米推送+云信推送
        miPushUtils.sendBroadcastAll(body, title, jsonObjectPayload, deviceType);
        //记录推送流水
        addSystemToPush(body, title);
    }*/


    /**
     * 活动通知
     *
     * @param
     */
    public void activeMessage(String title, String body, int postid) {
        try {
            Date date = new Date();
            //通知唯一标识
            long informidentity = date.getTime();
            ImUser imUser = this.getImuserByCurrentBossuser();
            //查询参加活动的人
            List<ImUser> imAppUserList = systemToPushService.queryUser(postid);
            if (ListUtil.isNotEmpty(imAppUserList)) {
                int size = imAppUserList.size();
                log.info("app中的IM用户共" + size + "人！");
                if (size > 500) {
                    //人数多于500人，分批次发系统通知
                    int mutiple = size / 500;   //倍数
                    for (int i = 0; i <= mutiple; i++) {
                        /**
                         * 比如共1002人，
                         * 那么i=0, 即第0-500人， 取500人
                         *     i=1, 即第501-1000人，    取500人
                         *     i=2, 即第1001-1002人，   取两人
                         */
                        int eachSize = i < mutiple ? 500 : size - mutiple * 500;
                        activeSendInform(body, imAppUserList, eachSize, imUser, i, informidentity, title, postid);
                    }
                } else {
                    //不超过500人
                    activeSendInform(body, imAppUserList, size, imUser, 0, informidentity, title, postid);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 活动
     *
     * @param body
     * @param imUser
     * @param multiple
     * @param informidentity
     * @throws IOException
     */
    private void activeSendInform(String body, List<ImUser> imAppUserList, int size, ImUser imUser, int multiple, long informidentity, String title, int activeid) throws IOException {
        //不足500人
        String toAccids = prepareToAccids(imAppUserList, size, multiple);
        Map result = this.sendSystemInformTo(body, imUser.getAccid(), toAccids);

        if (result.get("code").equals(200)) {
            log.info("发送系统通知成功，发送人accid=" + imUser.getAccid() + ",接收人accids=" + toAccids + ",发送内容=" + body);
            this.activeMessage(body, imUser.getAccid(), informidentity, toAccids, title, activeid);
        } else {
            throw new BusinessException(MsgCodeConstant.send_system_msg_fail, "发送系统通知失败");
        }

    }


    /**
     * 查询活动通知列表
     *
     * @param
     * @return
     */
    public List<ImSystemInform> findAllActiveMessage(String body, String pai, Paging<ImSystemInform> paging) {
        Map map = new HashMap();
        if (StringUtil.isNotEmpty(body)) {
            map.put("body", body);
        }
        if (StringUtil.isNotEmpty(pai)) {
            map.put("pai", pai);
        }
        return imSystemInformService.findAllActiveMessage(map, paging);
    }


    /**
     * 修改活动通知
     *
     * @param id
     * @return
     */
    public int updateActiveMessage(int id, String title, String body) {
        ImSystemInform imSystemInform = new ImSystemInform();
        if (String.valueOf(id) != null) {
            imSystemInform.setId(id);
        }
        if (title != null) {
            imSystemInform.setTitle(title);
        }
        if (body != null) {
            imSystemInform.setBody(body);
        }
        int re = imSystemInformService.updateActiveMessage(imSystemInform);
        return re;
    }

    /**
     * 活动通知回显
     *
     * @param id
     * @return
     */
    public ImSystemInform queryActiveMessageById(int id) {
        ImSystemInform imSystemInform = imSystemInformService.queryActiveById(id);
        return imSystemInform;
    }

    /**
     * 查询活动内容
     *
     * @param id
     * @return
     */
    public String queryActiveBody(int id) {
        String imsys = imSystemInformService.queryActiveBody(id);
        return imsys;
    }

    /**
     * 推送通公共方法
     *
     * @param userid       操作者
     * @param postid       被操作的帖子id
     * @param pushType     推送的类型：评论，点赞，打赏，关注
     * @param followedUser 被关注的用户id
     */
    public void sendPushByCommonWay(String userid, String postid, String pushType, String followedUser) {
        try {
            Map map = new HashMap();
            map.put("userid", userid);
            map.put("type", ImConstant.TYPE_APP);
            Map reMap = imUserService.queryAccidAndNickname(map);
            //获取推送人的accid
            String fromaccid = String.valueOf(reMap.get("accid"));
            //获取推送人的昵称
            String nickname = String.valueOf(reMap.get("nickname"));

            //获取被推送者的accid
            Map map2 = new HashMap();
            map2.put("type", ImConstant.TYPE_APP);
            String toAccid = null;
            if (StringUtils.isEmpty(postid)) {
                map2.put("userid", followedUser);
                Map reMap2 = imUserService.queryAccidAndNickname(map2);
                toAccid = String.valueOf(reMap2.get("accid"));
            } else {
                map2.put("postid", postid);
                toAccid = postService.selectAccid(map2);
            }

            //组合推送信息--显示在手机上
            String body = nickname + pushType + "了你";

            Map map3 = new HashMap();
            map3.put("body", body);
            Gson gson = new Gson();
            String json = gson.toJson(map3);

            sendMsgInform(json, fromaccid, toAccid, body);
        } catch (Exception e) {
            log.error("推送评论通知失败", e);
        }
    }


}
