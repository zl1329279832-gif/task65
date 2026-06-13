package com.controller;

import java.io.File;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.annotation.IgnoreAuth;

import com.entity.CartEntity;
import com.entity.view.CartView;

import com.service.CartService;
import com.service.OrdersService;
import com.service.TokenService;
import com.utils.PageUtils;
import com.utils.R;
import com.utils.MD5Util;
import com.utils.MPUtil;
import com.utils.CommonUtil;
import java.io.IOException;

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

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    @Autowired
    private CartService cartService;

    @Autowired
    private OrdersService ordersService;



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


    // ========================= 购物车结算下单（事务） =========================

    /**
     * 购物车结算接口
     * 一次性完成：校验库存 → 扣减商品库存 → 扣减仓储台账 → 生成订单 → 清空购物车
     * 全程 @Transactional，原子操作，并发安全（SQL 原子 UPDATE WHERE 防超卖）
     *
     * POST /cart/checkout
     * 请求体示例: { "address": "xxx", "tel": "13800000000", "consignee": "张三", "remark": "" }
     *
     * @return 成功返回订单编号 orderid
     */
    @RequestMapping("/checkout")
    @Transactional(rollbackFor = Exception.class)
    public R checkout(@RequestBody Map<String, String> params, HttpServletRequest request) {
        Long userId = (Long) request.getSession().getAttribute("userId");
        if (userId == null) {
            return R.error(401, "请先登录后再结算");
        }

        String address   = params.get("address");
        String tel       = params.get("tel");
        String consignee = params.get("consignee");
        String remark    = params.get("remark");

        if (StringUtils.isBlank(address)) {
            return R.error("收货地址不能为空");
        }
        if (StringUtils.isBlank(consignee)) {
            return R.error("收货人姓名不能为空");
        }

        try {
            String orderId = ordersService.checkoutFromCart(userId, address, tel, consignee, remark);
            return R.ok("下单成功，请尽快完成支付").put("data", orderId);
        } catch (com.entity.EIException e) {
            return R.error(e.getCode(), e.getMsg());
        } catch (Exception e) {
            log.error("结算异常", e);
            return R.error("结算失败: " + e.getMessage());
        }
    }


    // ========================= 扫码/拍照识别商品加购 =========================

    /**
     * 扫码/拍照识别商品并加入购物车
     * 上传图片 → 百度OCR识别文字 → 匹配商品 → 加入购物车
     *
     * POST /cart/recognize
     * 参数: file=图片文件, quantity=加购数量(可选，默认1)
     *
     * @return 成功返回识别到的商品信息；识别失败返回明确错误（不静默）
     */
    @RequestMapping("/recognize")
    public R recognize(@RequestParam("file") MultipartFile file,
                       @RequestParam(value = "quantity", defaultValue = "1") Integer quantity,
                       HttpServletRequest request) {
        Long userId = (Long) request.getSession().getAttribute("userId");
        if (userId == null) {
            return R.error(401, "请先登录后再使用扫码加购");
        }

        if (file == null || file.isEmpty()) {
            return R.error("请上传商品图片或条码照片");
        }

        // 保存图片到服务器
        String imagePath;
        try {
            File path = new File(ResourceUtils.getURL("classpath:static").getPath());
            if (!path.exists()) {
                path = new File("");
            }
            File upload = new File(path.getAbsolutePath(), "/upload/");
            if (!upload.exists()) {
                upload.mkdirs();
            }
            String fileExt = file.getOriginalFilename();
            if (fileExt != null && fileExt.contains(".")) {
                fileExt = fileExt.substring(fileExt.lastIndexOf(".") + 1);
            } else {
                fileExt = "jpg";
            }
            String fileName = "recognize_" + new Date().getTime() + "." + fileExt;
            File dest = new File(upload.getAbsolutePath() + "/" + fileName);
            file.transferTo(dest);
            imagePath = dest.getAbsolutePath();
        } catch (IOException e) {
            log.error("上传图片保存失败", e);
            return R.error("图片上传失败: " + e.getMessage());
        }

        try {
            Map<String, Object> result = ordersService.recognizeAndAddToCart(userId, imagePath, quantity);
            return R.ok("识别并加购成功").put("data", result);
        } catch (com.entity.EIException e) {
            return R.error(e.getCode(), e.getMsg());
        } catch (Exception e) {
            log.error("识别加购异常", e);
            return R.error("识别加购失败: " + e.getMessage());
        }
    }


}
