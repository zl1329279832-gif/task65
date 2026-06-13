package com.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.utils.PageUtils;
import com.utils.Query;
import com.utils.BaiduUtil;

import com.dao.OrdersDao;
import com.dao.CartDao;
import com.dao.ShangpinxinxiDao;
import com.dao.CangchuxinxiDao;
import com.dao.YonghuDao;
import com.entity.OrdersEntity;
import com.entity.CartEntity;
import com.entity.ShangpinxinxiEntity;
import com.entity.CangchuxinxiEntity;
import com.entity.YonghuEntity;
import com.entity.EIException;
import com.service.OrdersService;
import com.entity.vo.OrdersVO;
import com.entity.view.OrdersView;

@Service("ordersService")
public class OrdersServiceImpl extends ServiceImpl<OrdersDao, OrdersEntity> implements OrdersService {

    private static final Logger log = LoggerFactory.getLogger(OrdersServiceImpl.class);

    /** 订单合法状态常量 */
    public static final String STATUS_UNPAID    = "待支付";
    public static final String STATUS_PAID      = "已支付";
    public static final String STATUS_SHIPPED   = "已发货";
    public static final String STATUS_COMPLETED = "已完成";
    public static final String STATUS_CANCELLED = "已取消";

    /** 允许的状态跳转映射 */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = new HashMap<>();
    static {
        Set<String> fromUnpaid = new HashSet<>();
        fromUnpaid.add(STATUS_PAID);
        fromUnpaid.add(STATUS_CANCELLED);
        VALID_TRANSITIONS.put(STATUS_UNPAID, fromUnpaid);

        Set<String> fromPaid = new HashSet<>();
        fromPaid.add(STATUS_SHIPPED);
        fromPaid.add(STATUS_COMPLETED);
        VALID_TRANSITIONS.put(STATUS_PAID, fromPaid);

        Set<String> fromShipped = new HashSet<>();
        fromShipped.add(STATUS_COMPLETED);
        VALID_TRANSITIONS.put(STATUS_SHIPPED, fromShipped);
    }

    @Autowired
    private CartDao cartDao;

    @Autowired
    private ShangpinxinxiDao shangpinxinxiDao;

    @Autowired
    private CangchuxinxiDao cangchuxinxiDao;

    @Autowired
    private YonghuDao yonghuDao;


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        Page<OrdersEntity> page = this.selectPage(
                new Query<OrdersEntity>(params).getPage(),
                new EntityWrapper<OrdersEntity>()
        );
        return new PageUtils(page);
    }

    @Override
	public PageUtils queryPage(Map<String, Object> params, Wrapper<OrdersEntity> wrapper) {
		  Page<OrdersView> page =new Query<OrdersView>(params).getPage();
	        page.setRecords(baseMapper.selectListView(page,wrapper));
	    	PageUtils pageUtil = new PageUtils(page);
	    	return pageUtil;
 	}

    @Override
	public List<OrdersVO> selectListVO(Wrapper<OrdersEntity> wrapper) {
 		return baseMapper.selectListVO(wrapper);
	}

	@Override
	public OrdersVO selectVO(Wrapper<OrdersEntity> wrapper) {
 		return baseMapper.selectVO(wrapper);
	}

	@Override
	public List<OrdersView> selectListView(Wrapper<OrdersEntity> wrapper) {
		return baseMapper.selectListView(wrapper);
	}

	@Override
	public OrdersView selectView(Wrapper<OrdersEntity> wrapper) {
		return baseMapper.selectView(wrapper);
	}

    @Override
    public List<Map<String, Object>> selectValue(Map<String, Object> params, Wrapper<OrdersEntity> wrapper) {
        return baseMapper.selectValue(params, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params, Wrapper<OrdersEntity> wrapper) {
        return baseMapper.selectTimeStatValue(params, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectGroup(Map<String, Object> params, Wrapper<OrdersEntity> wrapper) {
        return baseMapper.selectGroup(params, wrapper);
    }


    // ========================= 购物车结算（事务） =========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String checkoutFromCart(Long userId, String address, String tel, String consignee, String remark) {
        if (userId == null) {
            throw new EIException("用户未登录，无法下单", 401);
        }

        // 1. 查询该用户的所有购物车项
        List<CartEntity> cartItems = cartDao.selectList(
                new EntityWrapper<CartEntity>().eq("userid", userId));
        if (cartItems == null || cartItems.isEmpty()) {
            throw new EIException("购物车为空，无法结算", 400);
        }

        // 生成统一订单号（年月日时分秒 + 4位随机数）
        String orderId = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", new Random().nextInt(10000));

        // 收集需要删除的购物车ID，事务成功后统一清理
        List<Long> cartIdsToDelete = new ArrayList<>();

        for (CartEntity cart : cartItems) {
            Long goodId = cart.getGoodid();
            int buyNumber = cart.getBuynumber();
            if (buyNumber <= 0) {
                throw new EIException("商品 [" + cart.getGoodname() + "] 购买数量无效", 400);
            }

            // 2. 查询商品信息（加锁读取用于校验，实际防超卖靠原子 UPDATE）
            ShangpinxinxiEntity product = shangpinxinxiDao.selectById(goodId);
            if (product == null) {
                throw new EIException("商品 [id=" + goodId + "] 不存在或已下架", 404);
            }

            // 校验单限 onelimittimes
            if (product.getOnelimittimes() != null && product.getOnelimittimes() > 0
                    && buyNumber > product.getOnelimittimes()) {
                throw new EIException("商品 [" + product.getShangpinmingcheng()
                        + "] 单次限购 " + product.getOnelimittimes() + " 件，当前购买 " + buyNumber + " 件", 400);
            }

            // 3. 原子扣减商品库存（Shangpinxinxi.alllimittimes），WHERE 条件防超卖
            int productAffected = shangpinxinxiDao.deductStock(goodId, buyNumber);
            if (productAffected == 0) {
                throw new EIException("商品 [" + product.getShangpinmingcheng()
                        + "] 库存不足，当前库存无法满足购买数量 " + buyNumber + " 件", 400);
            }

            // 4. 原子扣减仓储台账（Cangchuxinxi.alllimittimes），按商品编号匹配
            String shangpinbianhao = product.getShangpinbianhao();
            int warehouseAffected = cangchuxinxiDao.deductStock(shangpinbianhao, buyNumber);
            if (warehouseAffected == 0) {
                // 商品库存已扣但仓储不足 -> 回滚商品库存
                shangpinxinxiDao.restoreStock(goodId, buyNumber);
                throw new EIException("商品 [" + product.getShangpinmingcheng()
                        + "] 仓储台账不足，无法完成下单", 400);
            }

            // 5. 生成订单记录（每个购物车项对应一条 orders 记录）
            OrdersEntity order = new OrdersEntity();
            order.setId(new Date().getTime() + new Double(Math.floor(Math.random() * 1000)).longValue());
            order.setOrderid(orderId);
            order.setTablename("shangpinxinxi");
            order.setUserid(userId);
            order.setGoodid(goodId);
            order.setGoodname(product.getShangpinmingcheng());
            order.setPicture(product.getShangpintupian());
            order.setBuynumber(buyNumber);
            order.setPrice(product.getPrice());
            order.setDiscountprice(cart.getDiscountprice());
            // 总价格 = 单价 * 数量
            float total = product.getPrice() * buyNumber;
            order.setTotal(total);
            // 折扣总价
            float discountTotal = (cart.getDiscountprice() != null && cart.getDiscountprice() > 0)
                    ? cart.getDiscountprice() * buyNumber : total;
            order.setDiscounttotal(discountTotal);
            order.setType(1); // 默认余额支付
            order.setStatus(STATUS_UNPAID);
            order.setAddress(address);
            order.setTel(tel);
            order.setConsignee(consignee);
            order.setRemark(remark);
            order.setGoodtype(product.getShangpinfenlei());
            order.setAddtime(new Date());
            baseMapper.insert(order);

            cartIdsToDelete.add(cart.getId());
            log.info("订单生成成功: orderId={}, goodId={}, buyNumber={}, status={}",
                    orderId, goodId, buyNumber, STATUS_UNPAID);
        }

        // 6. 清空该用户的购物车
        if (!cartIdsToDelete.isEmpty()) {
            cartDao.deleteBatchIds(cartIdsToDelete);
            log.info("已清空用户 {} 的购物车，共 {} 项", userId, cartIdsToDelete.size());
        }

        return orderId;
    }


    // ========================= 订单状态流转（状态机） =========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(Long orderId, String targetStatus) {
        if (orderId == null) {
            throw new EIException("订单ID不能为空", 400);
        }
        if (targetStatus == null || targetStatus.trim().isEmpty()) {
            throw new EIException("目标状态不能为空", 400);
        }

        OrdersEntity order = baseMapper.selectById(orderId);
        if (order == null) {
            throw new EIException("订单不存在 [id=" + orderId + "]", 404);
        }

        String currentStatus = order.getStatus();

        // 状态机校验：拒绝非法跳转
        Set<String> allowed = VALID_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new EIException("非法状态跳转：当前状态 [" + currentStatus
                    + "] 不允许变更为 [" + targetStatus + "]", 400);
        }

        // 如果是取消订单（待支付 -> 已取消），需要回滚库存
        if (STATUS_CANCELLED.equals(targetStatus)) {
            restoreStockForOrder(order);
        }

        // 如果是支付完成（待支付 -> 已支付），扣减用户余额
        if (STATUS_PAID.equals(targetStatus) && STATUS_UNPAID.equals(currentStatus)) {
            deductUserBalance(order);
        }

        order.setStatus(targetStatus);
        baseMapper.updateById(order);
        log.info("订单状态变更: id={}, {} -> {}", orderId, currentStatus, targetStatus);
    }

    /**
     * 取消订单时恢复商品库存和仓储台账
     */
    private void restoreStockForOrder(OrdersEntity order) {
        Long goodId = order.getGoodid();
        int buyNumber = order.getBuynumber();

        // 恢复商品库存
        shangpinxinxiDao.restoreStock(goodId, buyNumber);

        // 恢复仓储台账
        ShangpinxinxiEntity product = shangpinxinxiDao.selectById(goodId);
        if (product != null && product.getShangpinbianhao() != null) {
            cangchuxinxiDao.restoreStock(product.getShangpinbianhao(), buyNumber);
        }
        log.info("已恢复订单 {} 的库存: goodId={}, quantity={}", order.getId(), goodId, buyNumber);
    }

    /**
     * 支付时扣减用户余额
     */
    private void deductUserBalance(OrdersEntity order) {
        Long userId = order.getUserid();
        float total = order.getTotal() != null ? order.getTotal() : 0f;

        YonghuEntity user = yonghuDao.selectById(userId);
        if (user == null) {
            throw new EIException("用户不存在 [id=" + userId + "]", 404);
        }
        float currentBalance = user.getMoney() != null ? user.getMoney() : 0f;
        if (currentBalance < total) {
            throw new EIException("用户余额不足，当前余额 " + currentBalance
                    + " 元，需要支付 " + total + " 元", 400);
        }
        user.setMoney(currentBalance - total);
        yonghuDao.updateById(user);
        log.info("已扣减用户 {} 余额 {} 元，剩余 {} 元", userId, total, user.getMoney());
    }


    // ========================= 扫码/拍照识别商品加购 =========================

    @Override
    public Map<String, Object> recognizeAndAddToCart(Long userId, String imagePath, int quantity) {
        if (userId == null) {
            throw new EIException("用户未登录，无法加购", 401);
        }
        if (imagePath == null || imagePath.trim().isEmpty()) {
            throw new EIException("图片路径不能为空", 400);
        }
        if (quantity <= 0) {
            quantity = 1;
        }

        // 1. 调用百度OCR识别图片文字
        String recognizedText;
        try {
            recognizedText = BaiduUtil.generalString(imagePath, false);
        } catch (Exception e) {
            log.error("百度OCR识别异常", e);
            throw new EIException("图片识别服务异常，请稍后重试: " + e.getMessage(), 500);
        }

        if (recognizedText == null || recognizedText.trim().isEmpty()) {
            throw new EIException("图片识别失败：未能从图片中提取到有效文字，请确保图片清晰包含商品名称或条码编号", 400);
        }

        log.info("OCR识别结果: {}", recognizedText);

        // 2. 在商品表中匹配：优先精确匹配商品编号，再模糊匹配商品名称
        ShangpinxinxiEntity matched = null;

        // 2a. 尝试精确匹配商品编号（识别文本包含编号）
        List<ShangpinxinxiEntity> allProducts = shangpinxinxiDao.selectList(
                new EntityWrapper<ShangpinxinxiEntity>());
        for (ShangpinxinxiEntity p : allProducts) {
            if (p.getShangpinbianhao() != null
                    && recognizedText.contains(p.getShangpinbianhao())) {
                matched = p;
                break;
            }
        }

        // 2b. 尝试模糊匹配商品名称
        if (matched == null) {
            for (ShangpinxinxiEntity p : allProducts) {
                if (p.getShangpinmingcheng() != null
                        && recognizedText.contains(p.getShangpinmingcheng())) {
                    matched = p;
                    break;
                }
            }
        }

        // 2c. 反向匹配：商品名称包含在识别文本中（较短的商品名）
        if (matched == null) {
            for (ShangpinxinxiEntity p : allProducts) {
                if (p.getShangpinmingcheng() != null && p.getShangpinmingcheng().length() >= 2
                        && p.getShangpinmingcheng().length() <= recognizedText.length()
                        && recognizedText.contains(p.getShangpinmingcheng())) {
                    matched = p;
                    break;
                }
            }
        }

        if (matched == null) {
            throw new EIException("图片识别成功但未能匹配到商品，识别文本: ["
                    + recognizedText + "]，请手动搜索商品或重新拍照", 404);
        }

        // 3. 校验库存
        if (matched.getAlllimittimes() == null || matched.getAlllimittimes() < quantity) {
            throw new EIException("商品 [" + matched.getShangpinmingcheng()
                    + "] 库存不足，当前库存 " + (matched.getAlllimittimes() != null ? matched.getAlllimittimes() : 0) + " 件", 400);
        }

        // 4. 加入购物车（追加模式：已有则数量+quantity，否则新增）
        List<CartEntity> existingCarts = cartDao.selectList(new EntityWrapper<CartEntity>()
                .eq("userid", userId)
                .eq("goodid", matched.getId()));
        CartEntity existingCart = (existingCarts != null && !existingCarts.isEmpty()) ? existingCarts.get(0) : null;

        if (existingCart != null) {
            existingCart.setBuynumber(existingCart.getBuynumber() + quantity);
            cartDao.updateById(existingCart);
        } else {
            CartEntity newCart = new CartEntity();
            newCart.setId(new Date().getTime() + new Double(Math.floor(Math.random() * 1000)).longValue());
            newCart.setTablename("shangpinxinxi");
            newCart.setUserid(userId);
            newCart.setGoodid(matched.getId());
            newCart.setGoodname(matched.getShangpinmingcheng());
            newCart.setPicture(matched.getShangpintupian());
            newCart.setBuynumber(quantity);
            newCart.setPrice(matched.getPrice());
            newCart.setDiscountprice(null);
            newCart.setGoodtype(matched.getShangpinfenlei());
            newCart.setAddtime(new Date());
            cartDao.insert(newCart);
        }

        log.info("扫码加购成功: userId={}, productId={}, productName={}, quantity={}",
                userId, matched.getId(), matched.getShangpinmingcheng(), quantity);

        // 5. 返回匹配到的商品信息
        Map<String, Object> result = new HashMap<>();
        result.put("productId", matched.getId());
        result.put("productName", matched.getShangpinmingcheng());
        result.put("productCode", matched.getShangpinbianhao());
        result.put("price", matched.getPrice());
        result.put("stock", matched.getAlllimittimes());
        result.put("recognizedText", recognizedText);
        result.put("quantity", quantity);
        return result;
    }

}
