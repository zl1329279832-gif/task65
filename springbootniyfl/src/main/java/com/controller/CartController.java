package com.controller;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import com.utils.ValidatorUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.annotation.IgnoreAuth;

import com.entity.CartEntity;
import com.entity.view.CartView;
import com.entity.ShangpinxinxiEntity;
import com.entity.CangchuxinxiEntity;
import com.entity.OrdersEntity;
import com.entity.AddressEntity;

import com.service.CartService;
import com.service.ShangpinxinxiService;
import com.service.CangchuxinxiService;
import com.service.OrdersService;
import com.service.AddressService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MD5Util;
import com.utils.MPUtil;
import com.utils.CommonUtil;
import com.utils.BaiduUtil;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import org.springframework.util.ResourceUtils;

/**
 * 购物车表
 * 后端接口
 * @author 
 * @email 
 * @date 2023-05-18 15:40:06
 */
@RestController
@RequestMapping("/cart")
public class CartController {
    @Autowired
    private CartService cartService;
    @Autowired
    private ShangpinxinxiService shangpinxinxiService;
    @Autowired
    private CangchuxinxiService cangchuxinxiService;
    @Autowired
    private OrdersService ordersService;
    @Autowired
    private AddressService addressService;


    


    /**
     * 后端列表
     */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params,CartEntity cart,
		HttpServletRequest request){
    	if(!request.getSession().getAttribute("role").toString().equals("管理员")) {
    		cart.setUserid((Long)request.getSession().getAttribute("userId"));
    	}
        EntityWrapper<CartEntity> ew = new EntityWrapper<CartEntity>();

		PageUtils page = cartService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, cart), params), params));

        return R.ok().put("data", page);
    }
    
    /**
     * 前端列表
     */
	@IgnoreAuth
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params,CartEntity cart, 
		HttpServletRequest request){
        EntityWrapper<CartEntity> ew = new EntityWrapper<CartEntity>();

		PageUtils page = cartService.queryPage(params, MPUtil.sort(MPUtil.between(MPUtil.likeOrEq(ew, cart), params), params));
        return R.ok().put("data", page);
    }

	/**
     * 列表
     */
    @RequestMapping("/lists")
    public R list( CartEntity cart){
       	EntityWrapper<CartEntity> ew = new EntityWrapper<CartEntity>();
      	ew.allEq(MPUtil.allEQMapPre( cart, "cart")); 
        return R.ok().put("data", cartService.selectListView(ew));
    }

	 /**
     * 查询
     */
    @RequestMapping("/query")
    public R query(CartEntity cart){
        EntityWrapper< CartEntity> ew = new EntityWrapper< CartEntity>();
 		ew.allEq(MPUtil.allEQMapPre( cart, "cart")); 
		CartView cartView =  cartService.selectView(ew);
		return R.ok("查询购物车表成功").put("data", cartView);
    }
	
    /**
     * 后端详情
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
        CartEntity cart = cartService.selectById(id);
        return R.ok().put("data", cart);
    }

    /**
     * 前端详情
     */
	@IgnoreAuth
    @RequestMapping("/detail/{id}")
    public R detail(@PathVariable("id") Long id){
        CartEntity cart = cartService.selectById(id);
        return R.ok().put("data", cart);
    }
    



    /**
     * 后端保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody CartEntity cart, HttpServletRequest request){
    	cart.setId(new Date().getTime()+new Double(Math.floor(Math.random()*1000)).longValue());
    	//ValidatorUtils.validateEntity(cart);
    	cart.setUserid((Long)request.getSession().getAttribute("userId"));
        cartService.insert(cart);
        return R.ok();
    }
    
    /**
     * 前端保存
     */
    @RequestMapping("/add")
    public R add(@RequestBody CartEntity cart, HttpServletRequest request){
    	cart.setId(new Date().getTime()+new Double(Math.floor(Math.random()*1000)).longValue());
    	//ValidatorUtils.validateEntity(cart);
        cartService.insert(cart);
        return R.ok();
    }



    /**
     * 修改
     */
    @RequestMapping("/update")
    @Transactional
    public R update(@RequestBody CartEntity cart, HttpServletRequest request){
        //ValidatorUtils.validateEntity(cart);
        cartService.updateById(cart);//全部更新
        return R.ok();
    }



    

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
        cartService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }

    /**
     * 购物车结算下单
     * 校验库存→扣减商品库存→扣减仓储台账→生成订单→清购物车
     */
    @RequestMapping("/checkout")
    @Transactional(rollbackFor = Exception.class)
    public R checkout(@RequestParam(required = false) Long addressId, HttpServletRequest request) {
        Long userId = (Long) request.getSession().getAttribute("userId");
        if (userId == null) {
            return R.error("请先登录");
        }

        // 1. 查询当前用户购物车
        List<CartEntity> cartList = cartService.selectList(
            new EntityWrapper<CartEntity>().eq("userid", userId));
        if (cartList == null || cartList.isEmpty()) {
            return R.error("购物车为空，无法下单");
        }

        // 2. 获取收货地址
        AddressEntity address = null;
        if (addressId != null) {
            address = addressService.selectById(addressId);
        }
        if (address == null) {
            address = addressService.selectOne(
                new EntityWrapper<AddressEntity>()
                    .eq("userid", userId)
                    .eq("isdefault", "是"));
        }

        // 3. 生成订单编号
        String orderid = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
            + new Double(Math.floor(Math.random() * 10000)).longValue();

        List<OrdersEntity> orderList = new ArrayList<>();

        // 4. 遍历购物车，校验库存并扣减
        for (CartEntity cart : cartList) {
            ShangpinxinxiEntity product = shangpinxinxiService.selectById(cart.getGoodid());
            if (product == null) {
                throw new RuntimeException("商品不存在：" + cart.getGoodname());
            }

            // 校验单次限购
            if (product.getOnelimittimes() != null && product.getOnelimittimes() > 0
                    && cart.getBuynumber() > product.getOnelimittimes()) {
                throw new RuntimeException("商品【" + product.getShangpinmingcheng()
                    + "】单次限购" + product.getOnelimittimes() + "件");
            }

            // 原子扣减商品库存（WHERE alllimittimes >= num 防超卖）
            int rows = shangpinxinxiService.deductStock(product.getId(), cart.getBuynumber());
            if (rows == 0) {
                throw new RuntimeException("商品【" + product.getShangpinmingcheng()
                    + "】库存不足，当前库存：" + product.getAlllimittimes());
            }

            // 扣减仓储台账
            cangchuxinxiService.deductStock(product.getShangpinbianhao(), cart.getBuynumber());

            // 构建订单记录
            OrdersEntity order = new OrdersEntity();
            order.setId(new Date().getTime() + new Double(Math.floor(Math.random() * 1000)).longValue());
            order.setOrderid(orderid);
            order.setTablename(cart.getTablename() != null ? cart.getTablename() : "shangpinxinxi");
            order.setUserid(userId);
            order.setGoodid(cart.getGoodid());
            order.setGoodname(cart.getGoodname());
            order.setPicture(cart.getPicture());
            order.setBuynumber(cart.getBuynumber());
            order.setPrice(product.getPrice());
            order.setDiscountprice(cart.getDiscountprice());
            order.setTotal(product.getPrice() * cart.getBuynumber());
            order.setDiscounttotal(cart.getDiscountprice() != null
                ? cart.getDiscountprice() * cart.getBuynumber() : null);
            order.setType(1);
            order.setStatus("待支付");
            order.setGoodtype(cart.getGoodtype());
            order.setAddtime(new Date());

            if (address != null) {
                order.setAddress(address.getAddress());
                order.setTel(address.getPhone());
                order.setConsignee(address.getName());
            }

            ordersService.insert(order);
            orderList.add(order);
        }

        // 5. 清空购物车
        cartService.delete(new EntityWrapper<CartEntity>().eq("userid", userId));

        return R.ok("下单成功").put("orderid", orderid).put("orders", orderList);
    }

    /**
     * 扫码/拍照识别商品加购
     * 通过百度OCR识别图片中的商品名称或编号，自动加入购物车
     */
    @RequestMapping("/addByImage")
    public R addByImage(@RequestParam String imageFile, HttpServletRequest request) {
        Long userId = (Long) request.getSession().getAttribute("userId");
        if (userId == null) {
            return R.error("请先登录");
        }

        // 1. 构建图片完整路径
        String imagePath;
        try {
            File path = new File(ResourceUtils.getURL("classpath:static").getPath());
            if (!path.exists()) {
                path = new File("");
            }
            imagePath = path.getAbsolutePath() + "/upload/" + imageFile;
            if (!new File(imagePath).exists()) {
                return R.error("图片文件不存在：" + imageFile);
            }
        } catch (Exception e) {
            return R.error("图片路径解析失败：" + e.getMessage());
        }

        // 2. 调用百度OCR识别
        String recognizedText;
        try {
            recognizedText = BaiduUtil.generalString(imagePath, false);
        } catch (Exception e) {
            return R.error("图片识别服务调用失败：" + e.getMessage());
        }
        if (recognizedText == null || recognizedText.trim().isEmpty()) {
            return R.error("图片识别失败，未能识别出商品信息，请确保图片清晰并包含商品名称或编号");
        }
        recognizedText = recognizedText.trim();

        // 3. 先精确匹配商品编号，再模糊匹配商品名称
        ShangpinxinxiEntity product = shangpinxinxiService.selectOne(
            new EntityWrapper<ShangpinxinxiEntity>().eq("shangpinbianhao", recognizedText));
        if (product == null) {
            List<ShangpinxinxiEntity> candidates = shangpinxinxiService.selectList(
                new EntityWrapper<ShangpinxinxiEntity>().like("shangpinmingcheng", recognizedText));
            if (candidates != null && !candidates.isEmpty()) {
                product = candidates.get(0);
            }
        }
        if (product == null) {
            return R.error("未找到匹配的商品，识别内容：" + recognizedText);
        }

        // 4. 已在购物车则数量+1
        CartEntity existingCart = cartService.selectOne(
            new EntityWrapper<CartEntity>().eq("userid", userId).eq("goodid", product.getId()));
        if (existingCart != null) {
            existingCart.setBuynumber(existingCart.getBuynumber() + 1);
            cartService.updateById(existingCart);
            return R.ok("商品已在购物车中，数量+1").put("data", existingCart);
        }

        // 5. 新增购物车记录
        CartEntity cart = new CartEntity();
        cart.setId(new Date().getTime() + new Double(Math.floor(Math.random() * 1000)).longValue());
        cart.setTablename("shangpinxinxi");
        cart.setUserid(userId);
        cart.setGoodid(product.getId());
        cart.setGoodname(product.getShangpinmingcheng());
        cart.setPicture(product.getShangpintupian());
        cart.setBuynumber(1);
        cart.setPrice(product.getPrice());
        cart.setGoodtype(product.getShangpinfenlei());
        cart.setAddtime(new Date());
        cartService.insert(cart);

        return R.ok("识别成功，已加入购物车").put("data", cart);
    }

}
