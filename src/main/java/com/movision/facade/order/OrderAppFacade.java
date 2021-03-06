package com.movision.facade.order;

import com.movision.mybatis.address.entity.Address;
import com.movision.mybatis.address.service.AddressService;
import com.movision.mybatis.cart.entity.CartVo;
import com.movision.mybatis.cart.service.CartService;
import com.movision.mybatis.combo.service.ComboService;
import com.movision.mybatis.coupon.entity.Coupon;
import com.movision.mybatis.coupon.service.CouponService;
import com.movision.mybatis.goods.service.GoodsService;
import com.movision.mybatis.goodsDiscount.entity.GoodsDiscount;
import com.movision.mybatis.goodsDiscount.service.DiscountService;
import com.movision.mybatis.invoice.entity.Invoice;
import com.movision.mybatis.invoice.entity.InvoiceVo;
import com.movision.mybatis.orders.entity.Orders;
import com.movision.mybatis.orders.service.OrderService;
import com.movision.mybatis.pointRecord.service.PointRecordService;
import com.movision.mybatis.rentdate.entity.Rentdate;
import com.movision.mybatis.subOrder.entity.SubOrder;
import com.movision.mybatis.subOrder.entity.SubOrderVo;
import com.movision.mybatis.user.service.UserService;
import com.movision.utils.CalculateFee;
import com.movision.utils.CheckStock;
import com.movision.utils.GenerateOrderNum;
import com.movision.utils.pagination.model.Paging;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * @Author shuxf
 * @Date 2017/3/9 09:40
 */
@Service
public class OrderAppFacade {

    private Logger log = LoggerFactory.getLogger(OrderAppFacade.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private AddressService addressService;

    @Autowired
    private CartService cartService;

    @Autowired
    private CalculateFee calculateFee;

    @Autowired
    private DiscountService discountService;

    @Autowired
    private ComboService comboService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private GenerateOrderNum generateOrderNum;

    @Autowired
    private UserService userService;

    @Autowired
    private PointRecordService pointRecordService;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private CheckStock checkStock;

    public List<Orders> getMyOrderList(Paging<Orders> paging, int userid) {
        Map map = new HashedMap();
        map.put("userid", userid);
        return orderService.findAllMyOrderList(paging, map);
    }

    @Transactional
    public Map<String, Object> commitOrder(String userid, String addressid, String cartids, String takeway, String kind, String onlystatue,
                                           String head, String content, String invoiceaddressid, String companyname, String rigaddress,
                                           String rigphone, String bank, String banknum, String code, String couponid, String points,
                                           String message, String logisticsfee, String totalprice, String payprice) {

        //首先根据cartids取出用户需要结算的所有商品
        String[] cartidarr = cartids.split(",");
        int[] cartid = new int[cartidarr.length];
        for (int i = 0; i < cartidarr.length; i++) {
            cartid[i] = Integer.parseInt(cartidarr[i]);
        }
        List<CartVo> cartVoList = cartService.queryCartVoList(cartid);//查询需要结算的购物车所有商品

        //此处再次计算运费，用于拆分的不同商家订单中运费的填充
        Address addr = addressService.queryAddressById(Integer.parseInt(addressid));
        //定义运费变量map
        Map<String, Object> feemap = new HashMap<>();

        int flag = 0;//商品和套餐库存校验标识
        int flag1 = 0;//商品是否下架校验标识
        int flag2 = 0;//商品租赁日期校验标识
        int flag3 = 0;//商品优惠活动校验标识
        int flag4 = 0;//订单总金额的校验标识
        int flag5 = 0;//实付总金额的校验标识
        int flag6 = 0;//运费总额的校验标识

        //调用公共计算接口计算运费(只有地址可用才会调用提交订单接口，所以此处不用判空也不用校验省市code是否可计算)
        if (addr != null) {//判空 防止空指针
            feemap = calculateFee.GetFee(cartVoList, addr.getLng(), addr.getLat());
        }

        //此处需要对订单中的商品进行二次校验
        double totalamount = 0;//订单总金额(=订单中自营商品的总金额+订单中第三方商品的总金额)
        double selfamount = 0;//订单中自营商品的总金额
        Map<Integer, Double> shopamountmap = new HashMap<>();//订单中第三方商品的金额分类map

        //由于购物车中可能会出现选择不同配置或套餐或者立即购买时临时生成的购物车记录，会导致购物车中出现同一个goodsid的商品出现多条的情况，
        //因此不能在遍历购物车时再轮流进行商品和套餐的库存判断（会出现多个套餐多条记录导致总购买件数大于商品库存的超卖情况）
        //所以这里需要对购物车中的记录按照商品的goodsid分组判断总购买件数，轮训判断所有商品的库存
        Map<String, Object> map = checkStock.checkGoodsStock(cartVoList);
        if ((int) map.get("stockcode") == -2) {
            flag = 1;
        }

        for (int i = 0; i < cartVoList.size(); i++) {

            int id = cartVoList.get(i).getId();//购物车id

            //校验商品是否已下架
            if (cartVoList.get(i).getIsdel() == 1) {
                map.put("delcode", 0);
                map.put("delcartid", id);
                map.put("delmsg", "商品已下架");
                flag1 = 1;
            }

            //1.校验所有商品库存//--------------------------参照333行说明
//            if (cartVoList.get(i).getStock() < cartVoList.get(i).getNum()) {
//                map.put("stockcode", -2);
//                map.put("stockcartid", id);
//                map.put("stockmsg", "商品库存不足");
//                flag = 1;
//            }

            //2.校验租赁商品的租赁日期
            if (cartVoList.get(i).getType() == 0) {//判断为租赁时才进行校验
                List<Rentdate> rentdateList = cartService.queryRentDateList(cartVoList.get(i).getId());
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                Date now = new Date();
                String nowString = formatter.format(now);
                String rentdateString;
                for (int j = 0; j < rentdateList.size(); j++) {
                    rentdateString = formatter.format(rentdateList.get(j).getRentdate());
                    if (now.after(rentdateList.get(j).getRentdate()) || nowString.equals(rentdateString)) {
                        map.put("rentdatecode", -3);
                        map.put("rentdatecartid", id);
                        map.put("rentdatemsg", "商品租赁日期必须从次日起租");
                        flag2 = 1;
                    }
                }
            }

            //3.校验商品套餐的库存//--------------------------参照333行说明
//            if (cartVoList.get(i).getCombotype() != null) {
//                //根据套餐id查询该套餐中所有的商品的库存
//                List<GoodsVo> goodsVos = cartService.queryGoodsByComboid(cartVoList.get(i).getCombotype());
//                for (int k = 0; k < goodsVos.size(); k++) {
//                    if (goodsVos.get(k).getStock() < cartVoList.get(i).getNum()) {
//                        map.put("combocode", -4);
//                        map.put("combocartid", id);
//                        map.put("combomsg", "该商品套餐中包含库存不足的商品");
//                        flag = 1;
//                    }
//                }
//            }

            //4.校验商品活动的起止日期（分为整租活动和非整租活动）
            if (cartVoList.get(i).getDiscountid() != null) {
                //根据活动id查询活动的开始时间和结束时间
                GoodsDiscount goodsDiscount = discountService.queryGoodsDiscountById(cartVoList.get(i).getDiscountid());
                if (null != goodsDiscount) {
                    Date startdate = goodsDiscount.getStartdate();
                    Date enddate = goodsDiscount.getEnddate();
                    Date now = new Date();
                    if (now.before(startdate) || now.after(enddate)) {
                        map.put("discountcode", -5);
                        map.put("discountcartid", id);
                        map.put("discountmsg", "该商品参与的优惠活动不在活动期间");
                        flag3 = 1;
                    }
                } else {
                    map.put("discountcode", -5);
                    map.put("discountcartid", id);
                    map.put("discountmsg", "该商品参与的优惠活动已下架");
                    flag3 = 1;
                }
            }

            if (flag == 0 && flag1 == 0 && flag2 == 0 && flag3 == 0) {//如果上面的校验全部通过，进行如下操作

                //5.计算选择的结算商品的总价格（与APP入参核对）
                if (cartVoList.get(i).getType() == 0) {//租赁
                    //查询租赁日期列表
                    List<Rentdate> rentdateList = cartService.queryRentDateList(id);

                    if (cartVoList.get(i).getCombotype() != null) {//包含套餐
                        //查询套餐的折后价
                        double comboprice = comboService.queryComboPrice(cartVoList.get(i).getCombotype());
                        if (cartVoList.get(i).getDiscountid() != null) {
                            //查询购物车商品参加活动的活动折扣
                            String discount = discountService.queryDiscount(cartVoList.get(i).getDiscountid());
                            //单个套餐总价=套餐价*套餐件数*天数*活动百分比（有活动）
                            double amount = comboprice * cartVoList.get(i).getNum() * rentdateList.size() * Integer.parseInt(discount) / 100;
                            totalamount = totalamount + amount;

                            if (cartVoList.get(i).getIsself() == 1) {//自营
                                selfamount = selfamount + amount;
                            } else {//三方

                                Iterator it = shopamountmap.entrySet().iterator();//每次计算店铺总金额时提前取出map的迭代对象it
                                //将三方店铺的商品金额归类累加，分别放入map中
                                shopamountmap = countShopAmount(it, cartVoList, i, shopamountmap, amount);
                            }

                        } else {
                            //单个套餐总价=套餐价*套餐件数*天数（无活动）
                            double amount = comboprice * cartVoList.get(i).getNum() * rentdateList.size();
                            totalamount = totalamount + amount;

                            if (cartVoList.get(i).getIsself() == 1) {//自营
                                selfamount = selfamount + amount;
                            } else {//三方

                                Iterator it = shopamountmap.entrySet().iterator();//每次计算店铺总金额时提前取出map的迭代对象it
                                //将三方店铺的商品金额归类累加，分别放入map中
                                shopamountmap = countShopAmount(it, cartVoList, i, shopamountmap, amount);
                            }

                        }
                    } else {//不包含套餐
                        if (cartVoList.get(i).getDiscountid() != null) {
                            //单个商品总价=商品折后价*商品件数*天数*活动百分比（有活动）
                            double amount = cartVoList.get(i).getGoodsprice() * cartVoList.get(i).getNum() * rentdateList.size() * Integer.parseInt(cartVoList.get(i).getDiscount()) / 100;
                            totalamount = totalamount + amount;

                            if (cartVoList.get(i).getIsself() == 1) {//自营
                                selfamount = selfamount + amount;
                            } else {//三方

                                Iterator it = shopamountmap.entrySet().iterator();//每次计算店铺总金额时提前取出map的迭代对象it
                                //将三方店铺的商品金额归类累加，分别放入map中
                                shopamountmap = countShopAmount(it, cartVoList, i, shopamountmap, amount);
                            }
                        } else {
                            //单个商品总价=商品折后价*商品件数*天数（无活动）
                            double amount = cartVoList.get(i).getGoodsprice() * cartVoList.get(i).getNum() * rentdateList.size();
                            totalamount = totalamount + amount;

                            if (cartVoList.get(i).getIsself() == 1) {//自营
                                selfamount = selfamount + amount;
                            } else {//三方

                                Iterator it = shopamountmap.entrySet().iterator();//每次计算店铺总金额时提前取出map的迭代对象it
                                //将三方店铺的商品金额归类累加，分别放入map中
                                shopamountmap = countShopAmount(it, cartVoList, i, shopamountmap, amount);
                            }
                        }
                    }

                } else if (cartVoList.get(i).getType() == 1 || cartVoList.get(i).getType() == 2) {
                    //出售或二手（出售可以多个；二手的商品库存只有1个）
                    if (cartVoList.get(i).getCombotype() != null) {//包含套餐
                        //查询套餐的折后价
                        double comboprice = comboService.queryComboPrice(cartVoList.get(i).getCombotype());
                        if (cartVoList.get(i).getDiscountid() != null) {
                            //单个套餐总价=套餐价*套餐件数*活动百分比（有活动）
                            double amount = comboprice * cartVoList.get(i).getNum() * Integer.parseInt(cartVoList.get(i).getDiscount()) / 100;
                            totalamount = totalamount + amount;

                            if (cartVoList.get(i).getIsself() == 1) {//自营
                                selfamount = selfamount + amount;
                            } else {//三方

                                Iterator it = shopamountmap.entrySet().iterator();//每次计算店铺总金额时提前取出map的迭代对象it
                                //将三方店铺的商品金额归类累加，分别放入map中
                                shopamountmap = countShopAmount(it, cartVoList, i, shopamountmap, amount);
                            }
                        } else {
                            //单个套餐总价=套餐价*套餐件数（无活动）
                            double amount = comboprice * cartVoList.get(i).getNum();
                            totalamount = totalamount + amount;

                            if (cartVoList.get(i).getIsself() == 1) {//自营
                                selfamount = selfamount + amount;
                            } else {//三方

                                Iterator it = shopamountmap.entrySet().iterator();//每次计算店铺总金额时提前取出map的迭代对象it
                                //将三方店铺的商品金额归类累加，分别放入map中
                                shopamountmap = countShopAmount(it, cartVoList, i, shopamountmap, amount);
                            }
                        }
                    } else {//不包含套餐
                        if (cartVoList.get(i).getDiscountid() != null) {//有活动
                            //单个商品总价=商品折后价*商品件数*活动百分比（有活动）
                            double amount = cartVoList.get(i).getGoodsprice() * cartVoList.get(i).getNum() * Integer.parseInt(cartVoList.get(i).getDiscount()) / 100;
                            totalamount = totalamount + amount;

                            if (cartVoList.get(i).getIsself() == 1) {//自营
                                selfamount = selfamount + amount;
                            } else {//三方

                                Iterator it = shopamountmap.entrySet().iterator();//每次计算店铺总金额时提前取出map的迭代对象it
                                //将三方店铺的商品金额归类累加，分别放入map中
                                shopamountmap = countShopAmount(it, cartVoList, i, shopamountmap, amount);
                            }
                        } else {//无活动
                            //单个商品总价=商品折后价*商品件数（无活动）
                            double amount = cartVoList.get(i).getGoodsprice() * cartVoList.get(i).getNum();
                            totalamount = totalamount + amount;

                            if (cartVoList.get(i).getIsself() == 1) {//自营
                                selfamount = selfamount + amount;
                            } else {//三方

                                Iterator it = shopamountmap.entrySet().iterator();//每次计算店铺总金额时提前取出map的迭代对象it
                                //将三方店铺的商品金额归类累加，分别放入map中
                                shopamountmap = countShopAmount(it, cartVoList, i, shopamountmap, amount);
                            }
                        }
                    }
                }
            }
        }

        if (flag == 0 && flag1 == 0 && flag2 == 0 && flag3 == 0) {
            //此处需要对订单总额进行核对
            if (Double.parseDouble(totalprice) != totalamount) {
                log.info("服务器计算的订单总额>>>>>>>>>>>>>>>>" + totalamount);
                log.info("服务器计算的自营订单总额>>>>>>>>>>>>>>>>" + selfamount);
                map.put("totalcode", -1);
                map.put("totalmsg", "提交的订单总额和服务器订单总额不一致");
                flag4 = 1;
            }

            double payamount = totalamount;//系统计算得到的实际支付金额
            //如果优惠券id不为空
            Coupon coupon = new Coupon();
            if (!StringUtils.isEmpty(couponid)) {
                //根据优惠券id查询优惠券实体
                coupon = couponService.queryCouponById(Integer.parseInt(couponid));
                payamount = payamount - coupon.getTmoney();
            }
            //如果使用积分数不为空
            if (!StringUtils.isEmpty(points)) {
                //100分抵扣1元
                payamount = payamount - Double.parseDouble(points) / 100;
            }

            //此处需要对运费总额进行校验
            if (Double.parseDouble(logisticsfee) != (double) ((long) feemap.get("totalfee"))) {
                log.info("服务器计算的运费总额>>>>>>>>>>>>>>>>" + feemap.get("totalfee"));
                map.put("feecode", -6);
                map.put("feemsg", "提交的运费总额和服务器运费总额不一致");
                flag6 = 1;
            }

            //实付款=订单总额-优惠券金额-积分优惠金额+运费总额
            payamount = payamount + (double) ((long) feemap.get("totalfee"));

            //此处需要对订单实际支付金额进行核对
            if (Double.parseDouble(payprice) != payamount) {
                log.info("服务器计算的实付金额>>>>>>>>>>>>>>>>" + payamount);
                map.put("paycode", 0);
                map.put("paymsg", "提交的实付金额和服务器实付金额不一致");
                flag5 = 1;
            }

            //------------------------------debug各店铺商品的总金额-----------------------
            Iterator its = shopamountmap.entrySet().iterator();
            while (its.hasNext()) {
                Entry entry = (Entry) its.next();
                log.info("测试打印的key>>>>>>>>>>>>>>>>>>>" + entry.getKey());     //返回对应的键
                log.info("测试打印的value>>>>>>>>>>>>>>>>>>>" + entry.getValue());   //返回对应的值
            }
            //------------------------------debug各店铺商品的总金额-----------------------

            //除了基础校验之外以上三项金额校验也通过的话，进行订单提交
            if (flag4 == 0 && flag5 == 0 && flag6 == 0) {//批量提交（引入申明式事务）

                //取出所有店铺id
                List<Integer> shoplist = new ArrayList<>();
                //对所有的shopid遍历去重
                for (int i = 0; i < cartVoList.size(); i++) {
                    int mark = 0;
                    for (int j = 0; j < shoplist.size(); j++) {
                        if (cartVoList.get(i).getShopid() == shoplist.get(j)) {
                            mark = 1;
                        }
                    }
                    if (mark == 0) {
                        shoplist.add(cartVoList.get(i).getShopid());
                    }
                }

                List<Integer> orderidlsit = new ArrayList<>();//记录生成的所有订单号
                for (int i = 0; i < shoplist.size(); i++) {

                    log.info("提交订单的店铺id>>>>>>>>>>>>>>>>>>>>>>>>>>" + shoplist.get(i));
                    double realpayamount = 0;//定义当前遍历的这个店铺订单的实际支付金额
                    if (shoplist.get(i) == -1) {
                        realpayamount = selfamount;
                    } else {
                        realpayamount = shopamountmap.get(shoplist.get(i));
                    }

                    //定义一个list集合存放当前遍历的这个店铺的商品
                    List<CartVo> shopCartList = new ArrayList<>();
                    for (int j = 0; j < cartVoList.size(); j++) {
                        if (shoplist.get(i) == cartVoList.get(j).getShopid()) {//如果从所有购物车商品中找到shopid和当前的shopid一样的商品就加入shopCartList
                            shopCartList.add(cartVoList.get(j));
                        }
                    }

                    //------------------------------------下面开始拼装入参，统一放到service曾进行事务提交------------------------------------
                    //提交主订单（批量提交）
                    String ordernumber = generateOrderNum.getOrderNum(shoplist.get(i));//调用生成订单号工具方法生成订单编号

                    Orders orders = new Orders();
                    orders.setOrdernumber(ordernumber);
                    orders.setUserid(Integer.parseInt(userid));
                    orders.setAddressid(Integer.parseInt(addressid));
                    orders.setTakeway(Integer.parseInt(takeway));
                    orders.setStatus(0);//生成后订单状态设为待支付
                    if (kind.equals("1")) {
                        orders.setBill(head);
                    } else if (kind.equals("2")) {
                        orders.setBill(companyname);
                    }
                    if (shoplist.get(i) == -1) {
                        orders.setMoney(selfamount);
                    } else {
                        orders.setMoney(shopamountmap.get(shoplist.get(i)));
                    }
                    if (null != feemap.get(String.valueOf(shoplist.get(i)))) {
                        orders.setSendmoney(new Double(feemap.get(String.valueOf(shoplist.get(i))).toString()));
                    } else {
                        orders.setSendmoney(0.0);
                    }
                    if (!StringUtils.isEmpty(couponid)) {//使用了优惠券
                        if ((i == 0 && coupon.getType() != 2) || (coupon.getType() == 2 && coupon.getShopid() == shoplist.get(i))) {//非店铺券且为第一家店直接使用   或者  店铺优惠券 且 当前店铺id等于优惠券上的店铺id可使用
                            orders.setIsdiscount(1);
                            orders.setCouponsid(couponid);
                            orders.setDiscouponmoney(coupon.getTmoney());

                            realpayamount = realpayamount - coupon.getTmoney();//实际支付金额扣减
                        } else {
                            orders.setIsdiscount(0);//只使用了一张优惠券，除了上面两种情况之外的情况全部设为0 未使用优惠券
                        }
                    } else {//未使用优惠券
                        orders.setIsdiscount(0);
                    }
                    if (!StringUtils.isEmpty(points)) {//使用了积分
                        if (i == 0) {
                            orders.setDispointmoney(Double.parseDouble(points) / 100);

                            realpayamount = realpayamount - Double.parseDouble(points) / 100;//实际支付金额扣减
                        }
                    }
                    orders.setIntime(new Date());
                    if (!StringUtils.isEmpty(message)) {
                        orders.setRemark(message);
                    }
                    orders.setIsdel(0);
                    //-------------------------------------------------------------------拼装主订单对象完成
                    orderService.insertOrders(orders);//插入返回主键订单id
                    int orderid = orders.getId();//主订单id
                    orderidlsit.add(orderid);

                    //提交子订单(由于一个订单中会出现多个商品，所以封装子订单list)
                    for (int j = 0; j < shopCartList.size(); j++) {
                        SubOrder subOrder = new SubOrder();
                        subOrder.setPorderid(orderid);
                        subOrder.setGoodsid(shopCartList.get(j).getGoodsid());
                        if (shopCartList.get(j).getCombotype() != null) {
                            subOrder.setCombotype(shopCartList.get(j).getCombotype());
                        }
                        if (shopCartList.get(j).getDiscountid() != null) {
                            subOrder.setDiscountid(shopCartList.get(j).getDiscountid());
                        }
                        subOrder.setSum(shopCartList.get(j).getNum());
                        if (shopCartList.get(j).getIsdebug() != null) {
                            subOrder.setIsdebug(shopCartList.get(j).getIsdebug());
                        }
                        subOrder.setIsdel(0);
                        subOrder.setType(shopCartList.get(j).getType());

                        //orderService.batchInsertOrders(subOrderList);//批量插入子订单表中的各个子订单----------(这里不能使用批量插入，否则会出现不好返回子订单主键，无法同步更新租赁日期中的购物车id为子订单id--shuxf2017.11.03)
                        orderService.insertSubOrders(subOrder);
                        int suborderid = subOrder.getId();

                        //提交子订单的同时去除库存
                        goodsService.deductStock(subOrder);

                        //循环中逐条将yw_rate_date租赁日期记录的购物车id更新为子订单id
                        Map<String, Object> parammap = new HashMap<>();
                        parammap.put("cartid", shopCartList.get(j).getId());
                        parammap.put("suborderid", suborderid);
                        orderService.updateRentDate(parammap);

                    }

                    //增加发票信息
                    Invoice invoice = new Invoice();
                    invoice.setOrderid(orderid);
                    if (kind.equals("1")) {
                        invoice.setHead(head);
                    } else if (kind.equals("2")) {
                        invoice.setHead(companyname);
                    }
                    invoice.setAddressid(Integer.parseInt(invoiceaddressid));
                    invoice.setMoney(realpayamount);//开票金额为实际支付金额
                    invoice.setKind(Integer.parseInt(kind));
                    invoice.setContent(content);
                    if (!StringUtils.isEmpty(companyname)) {
                        invoice.setCompanyname(companyname);
                    }
                    if (!StringUtils.isEmpty(rigaddress)) {
                        invoice.setRigaddress(rigaddress);
                    }
                    if (!StringUtils.isEmpty(rigphone)) {
                        invoice.setRigphone(rigphone);
                    }
                    if (!StringUtils.isEmpty(bank)) {
                        invoice.setBank(bank);
                    }
                    if (!StringUtils.isEmpty(banknum)) {
                        invoice.setBanknum(banknum);
                    }
                    if (!StringUtils.isEmpty(code)) {
                        invoice.setCode(code);
                    }
                    if (!StringUtils.isEmpty(onlystatue)) {
                        invoice.setOnlystatue(Integer.parseInt(onlystatue));
                    }
                    //-------------------------------------------------------------------拼装发票信息对象完成
                    orderService.insertInvoice(invoice);

                    //删除购物车中对应的商品(批量删除)
                    cartService.batchDeleteCartGoods(cartid);
                }

                //扣除优惠券（根据优惠券id进行扣除）
                if (!StringUtils.isEmpty(couponid)) {//使用了优惠券
                    couponService.useCoupon(Integer.parseInt(couponid));
                }

                //扣除积分（扣总分 加流水）
                if (!StringUtils.isEmpty(points)) {
                    Map<String, Object> parammap = new HashMap<>();
                    parammap.put("userid", Integer.parseInt(userid));
                    parammap.put("points", Integer.parseInt(points));
                    userService.usePoints(parammap);

                    parammap.put("isadd", 1);
                    parammap.put("type", 21);//type为21表示积分抵用现金
                    parammap.put("orderid", orderidlsit.get(0));
                    parammap.put("intime", new Date());
                    parammap.put("isdel", 0);
                    pointRecordService.inserRecord(parammap);
                }

                map.put("code", 200);
                map.put("msg", "订单生成成功");
                map.put("orderidlsit", orderidlsit);
            } else {
                map.put("code", 300);
                map.put("msg", "提交的订单金额与服务端计算金额校验不通过");
            }
        } else {
            map.put("code", -1);
            map.put("msg", "订单商品信息校验不通过");
        }

        //这里对map进行处理
        Set keys = map.keySet();
        int keymark1 = 0;
        int keymark2 = 0;
        int keymark3 = 0;
        int keymark4 = 0;
        int keymark5 = 0;
        int keymark6 = 0;
        //检索
        for (Object obj : keys) {
            if (obj.toString().equals("delcode")) {
                keymark1 = 1;
            }
            if (obj.toString().equals("rentdatecode")) {
                keymark2 = 1;
            }
            if (obj.toString().equals("discountcode")) {
                keymark3 = 1;
            }
            if (obj.toString().equals("totalcode")) {
                keymark4 = 1;
            }
            if (obj.toString().equals("paycode")) {
                keymark5 = 1;
            }
            if (obj.toString().equals("feecode")) {
                keymark6 = 1;
            }

        }
        if (keymark1 == 0) {
            map.put("delcode", 200);
            map.put("delmsg", "商品处于上架状态");
        }
        if (keymark2 == 0) {
            map.put("rentdatecode", 200);
            map.put("rentdatemsg", "商品租赁日期符合");
        }
        if (keymark3 == 0) {
            map.put("discountcode", 200);
            map.put("discountmsg", "商品选择的活动有效");
        }
        if (keymark4 == 0) {
            map.put("totalcode", 200);
            map.put("totalmsg", "提交的订单总金额校验通过");
        }
        if (keymark5 == 0) {
            map.put("paycode", 200);
            map.put("paymsg", "实付总金额校验通过");
        }
        if (keymark6 == 0) {
            map.put("feecode", 200);
            map.put("feemsg", "运费总金额校验通过");
        }

        return map;
    }

    /**
     * 将三方店铺的商品金额归类累加，分别放入map中(公用方法)
     *
     * @param it
     * @param cartVoList
     * @param i
     * @param shopamountmap
     * @param amount
     * @return
     */
    public Map<Integer, Double> countShopAmount(Iterator it, List<CartVo> cartVoList, int i, Map<Integer, Double> shopamountmap, double amount) {

        //将三方店铺的商品金额归类累加，分别放入map中
        int mark = 0;//标记
        //检查是否包含该shopid的订单总金额
        while (it.hasNext()) {
            Entry entry = (Entry) it.next();
            if (cartVoList.get(i).getShopid() == (int) entry.getKey()) {
                mark = 1;
            }
        }
        if (mark == 0) {//没有相同的shopid,直接put到map里
            shopamountmap.put(cartVoList.get(i).getShopid(), amount);
        } else if (mark == 1) {//有相同的shopid,取出累加再放入
            shopamountmap.put(cartVoList.get(i).getShopid(), shopamountmap.get(cartVoList.get(i).getShopid()) + amount);
        }
        return shopamountmap;
    }

    /**
     * 根据订单id查询订单详情
     */
    public Map<String, Object> queryOrderDetail(String orderid) {

        Map<String, Object> map = new HashMap<>();
        //查询订单基本信息
        Orders orders = orderService.getOrderById(Integer.parseInt(orderid));
        map.put("orders", orders);

        //查询订单收货地址和地址id
        int addressid = orders.getAddressid();//收货地址id
        Address address = addressService.queryAddressById(addressid);
        map.put("address", address);

        //查询单个订单中包含的所有商品和子订单id（即订单中的子订单id）
        List<SubOrderVo> subOrderVoList = orderService.querySubOrderListById(Integer.parseInt(orderid));
        //查询子订单的其他附属信息
        for (int i = 0; i < subOrderVoList.size(); i++) {
            Map<String, Object> parammap = new HashMap<>();
            parammap.put("goodsid", subOrderVoList.get(i).getGoodsid());
//            if (null != subOrderVoList.get(i).getCombotype()) {
            parammap.put("combotype", subOrderVoList.get(i).getCombotype());
//            }
//            if (null != subOrderVoList.get(i).getDiscountid()){
            parammap.put("discountid", subOrderVoList.get(i).getDiscountid());
//            }
            SubOrderVo vo = orderService.querySubOrderInfo(parammap);
            subOrderVoList.get(i).setImgurl(vo.getImgurl());
            subOrderVoList.get(i).setGoodsname(vo.getGoodsname());
            subOrderVoList.get(i).setGoodsprice(vo.getGoodsprice());
            subOrderVoList.get(i).setComboprice(vo.getComboprice());
            subOrderVoList.get(i).setOnline(vo.getOnline());
            subOrderVoList.get(i).setDiscount(vo.getDiscount());
            subOrderVoList.get(i).setComboname(vo.getComboname());
            subOrderVoList.get(i).setDiscountname(vo.getDiscountname());
        }
        map.put("subOrderVoList", subOrderVoList);

        //查询发票抬头和发票id
        Invoice invoice = orderService.queryInvoiceInfo(Integer.parseInt(orderid));
        map.put("invoice", invoice);

        return map;
    }

    /**
     * APP端用户根据14位订单编号对订单进行确认收货
     */
    public void confirmReceipt(String ordernumber) {
        orderService.confirmReceipt(ordernumber);
    }

    /**
     * APP端通过订单查询发票信息接口
     */
    public InvoiceVo queryInvoice(String ordernumber) {
        return orderService.queryInvoice(ordernumber);
    }

    /**
     * 根据发票id更新发票信息
     */
    public int updateInvoiceInfo(String id, String kind, String onlystatue, String head, String content, String invoiceaddressid, String companyname,
                                 String rigaddress, String rigphone, String bank, String banknum, String code) {
        //增加发票信息
        Invoice invoice = new Invoice();
        invoice.setId(Integer.parseInt(id));
        if (kind.equals("1")) {
            invoice.setHead(head);
        } else if (kind.equals("2")) {
            invoice.setHead(companyname);
        }
        invoice.setAddressid(Integer.parseInt(invoiceaddressid));
        invoice.setKind(Integer.parseInt(kind));
        invoice.setContent(content);
        if (!StringUtils.isEmpty(companyname)) {
            invoice.setCompanyname(companyname);
        }
        if (!StringUtils.isEmpty(rigaddress)) {
            invoice.setRigaddress(rigaddress);
        }
        if (!StringUtils.isEmpty(rigphone)) {
            invoice.setRigphone(rigphone);
        }
        if (!StringUtils.isEmpty(bank)) {
            invoice.setBank(bank);
        }
        if (!StringUtils.isEmpty(banknum)) {
            invoice.setBanknum(banknum);
        }
        if (!StringUtils.isEmpty(code)) {
            invoice.setCode(code);
        }
        if (!StringUtils.isEmpty(onlystatue)) {
            invoice.setOnlystatue(Integer.parseInt(onlystatue));
        }

        return orderService.updateInvoiceInfo(invoice);
    }
}
