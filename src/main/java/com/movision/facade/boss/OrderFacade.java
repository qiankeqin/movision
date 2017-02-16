package com.movision.facade.boss;

import com.movision.mybatis.area.entity.Area;
import com.movision.mybatis.bossOrders.entity.BossOrders;
import com.movision.mybatis.bossOrders.entity.BossOrdersVo;
import com.movision.mybatis.bossOrders.servic.BossOrderService;
import com.movision.mybatis.city.entity.City;
import com.movision.mybatis.post.entity.Post;
import com.movision.mybatis.province.entity.Province;
import com.movision.mybatis.user.service.UserService;
import com.movision.utils.pagination.model.Paging;
import com.movision.utils.pagination.util.StringUtils;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @Author zhurui
 * @Date 2017/2/6 10:31
 */
@Service
public class OrderFacade {
    @Autowired
    BossOrderService bossOrderService = new BossOrderService();

    @Autowired
    UserService userService = new UserService();

    /**
     * 查询订单列表
     *
     * @param pageNo
     * @param pageSize
     * @return
     */
    public List<BossOrdersVo> queryOrderList(String pageNo, String pageSize) {
        if (StringUtils.isEmpty(pageNo)) {
            pageNo = "1";
        }
        if (StringUtils.isEmpty(pageSize)) {
            pageSize = "10";
        }
        Paging<Post> pager = new Paging<Post>(Integer.parseInt(pageNo), Integer.parseInt(pageSize));
        return bossOrderService.queryOrderList(pager);
    }

    /**
     * 查询基本信息
     * @param
     * @param id
     * @return
     */
    public BossOrders queryOrderInfo(Integer id){
        BossOrders bossOrders = bossOrderService.queryOrderInfo(id);
        return bossOrders;
      }

    /**
     * 根据条件查询订单
     *
     * @param ordernumber
     * @param name
     * @param status
     * @param takeway
     * @return
     */
    public List<BossOrdersVo> queryOrderByCondition(String ordernumber, String name, String status, String takeway) {
        Map<String, String> map = new HashedMap();
        map.put("ordernumber", ordernumber);
        map.put("name", name);
        map.put("status", status);
        map.put("takeway", takeway);
        return bossOrderService.queryOrderByCondition(map);
    }

    /**
     * 订单查询
     *
     * @param ordernumber
     * @param province
     * @param city
     * @param district
     * @param takeway
     * @param mintime
     * @param maxtime
     * @param email
     * @param name
     * @param phone
     * @param paytype
     * @return
     */
    public List<BossOrdersVo> queryAccuracyConditionByOrder(String ordernumber,
                                                              String province, String city, String district, String takeway, String mintime,
                                                              String maxtime, String email, String name, String phone, String paytype) {

        Map<String, Object> map = new HashedMap();
        map.put("ordernumber",ordernumber);
        if(province!=null) {
            map.put("province", province);
        }
        if(city!=null) {
            map.put("city", city);
        }
        if(district!=null) {
            map.put("district", district);
        }
        if(takeway!=null){
            map.put("takeway",Integer.parseInt(takeway));
        }
        Date isessencetime = null;//开始时间
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        if (mintime != null) {
            try {
                isessencetime = format.parse(mintime);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        map.put("mintime",isessencetime);
        Date max=null;//最大时间
        if (maxtime != null) {
            try {
                max = format.parse(maxtime);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        map.put("maxtime",max);
        map.put("email",email);
        map.put("name",name);
        map.put("phone",phone);
        if(paytype!=null) {
            map.put("paytype", Integer.parseInt(paytype));
        }
        return  bossOrderService.queryAccuracyConditionByOrder(map);

     }

    public BossOrdersVo queryOrderParticulars(String ordernumber) {
        BossOrdersVo bo = bossOrderService.queryOrderParticulars(Integer.parseInt(ordernumber));
        Integer userid = bo.getUserid();
        String wxid = userService.queryUserByOpenid(userid);
        bo.setOpenid(wxid);
        return bo;
    }

    /**
     * 三级联动
     * @return
     */
    public  Map<String,Object> queryPostProvince(){
        Map<String, Object> map = new HashedMap();
        List<Integer> list =bossOrderService.queryPostProvince();
        List<Integer> listcity =bossOrderService.queryPostCity();
        List<Integer> listarea =bossOrderService.queryPostArea();
        List<List<Province>> proname = new ArrayList<>();
        List<List<City>> cityname = new ArrayList<>();
        List<List<Area>> areaname = new ArrayList<>();
        for (int i=0;i<list.size();i++){
            List<Province> province = bossOrderService.queryPostProvinceName(list.get(i));
            proname.add(province);
            for (int j=0;j<listcity.size();j++){
                List<City> city = bossOrderService.queryPostCityName(list.get(i));
                cityname.add(city);
                for (int k=0;k<listarea.size();k++){
                    List<Area> area = bossOrderService.queryPostAreaName(listcity.get(i));
                    areaname.add(area);
                }
            }
        }
        Integer pronum = bossOrderService.queryPostProvinceNum();
        Integer citynum = bossOrderService.queryPostCityNum();
        Integer areanum = bossOrderService.queryPostAreaNum();
        map.put("proname",proname);
        map.put("cityname",cityname);
        map.put("areaname",areaname);
        map.put("pronum",pronum);
        map.put("citynum",citynum);
        map.put("areanum",areanum);
        return map;
    }
}
