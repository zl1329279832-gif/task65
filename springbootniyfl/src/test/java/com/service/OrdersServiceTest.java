package com.service;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.dao.*;
import com.entity.*;
import com.service.impl.OrdersServiceImpl;
import com.utils.BaiduUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrdersService 业务层单元测试
 * 覆盖：购物车结算下单、订单状态流转、扫码识别加购、库存扣减防超卖
 *
 * 注意：pom.xml 中 skipTests=true，这些测试不会在 Maven 构建时自动运行。
 * 手动运行：mvn test -DskipTests=false -pl . -Dtest=OrdersServiceTest
 * 或在 IDE 中直接右键运行。
 *
 * 集成验证说明：
 * 1. 准备 MySQL 数据库（springbootniyfl），确保 shangpinxinxi / cangchuxinxi / cart / orders / yonghu 表存在
 * 2. 插入测试数据：至少 1 条商品（alllimittimes >= 10）、1 条仓储记录（同商品编号，alllimittimes >= 10）、1 条用户记录
 * 3. 启动应用后调用 POST /cart/checkout 验证端到端流程
 * 4. 并发测试：使用 JMeter 或 ab 同时对 /cart/checkout 发起 10+ 请求，确认无超卖
 */
@ExtendWith(MockitoExtension.class)
public class OrdersServiceTest {

    @InjectMocks
    private OrdersServiceImpl ordersService;

    @Mock
    private OrdersDao ordersDao; // baseMapper

    @Mock
    private CartDao cartDao;

    @Mock
    private ShangpinxinxiDao shangpinxinxiDao;

    @Mock
    private CangchuxinxiDao cangchuxinxiDao;

    @Mock
    private YonghuDao yonghuDao;

    @Mock
    private BaiduUtil baiduUtil;

    // 测试数据
    private CartEntity cartItem;
    private ShangpinxinxiEntity product;
    private CangchuxinxiEntity warehouse;
    private YonghuEntity user;

    @BeforeEach
    public void setUp() {
        // 商品
        product = new ShangpinxinxiEntity();
        product.setId(1001L);
        product.setShangpinbianhao("SP001");
        product.setShangpinmingcheng("测试可乐");
        product.setShangpintupian("cola.jpg");
        product.setShangpinfenlei("饮料");
        product.setAlllimittimes(50);
        product.setOnelimittimes(10);
        product.setPrice(5.0f);

        // 购物车项
        cartItem = new CartEntity();
        cartItem.setId(2001L);
        cartItem.setUserid(100L);
        cartItem.setGoodid(1001L);
        cartItem.setGoodname("测试可乐");
        cartItem.setPicture("cola.jpg");
        cartItem.setBuynumber(2);
        cartItem.setPrice(5.0f);
        cartItem.setDiscountprice(4.5f);
        cartItem.setTablename("shangpinxinxi");

        // 仓储
        warehouse = new CangchuxinxiEntity();
        warehouse.setId(3001L);
        warehouse.setShangpinbianhao("SP001");
        warehouse.setShangpinmingcheng("测试可乐");
        warehouse.setAlllimittimes(100);

        // 用户
        user = new YonghuEntity();
        user.setId(100L);
        user.setYonghuzhanghao("testuser");
        user.setMoney(1000.0f);
    }

    // ========================= checkoutFromCart 测试 =========================

    @Test
    public void testCheckoutFromCart_Success() {
        // 模拟购物车查询
        when(cartDao.selectList(any(EntityWrapper.class))).thenReturn(Arrays.asList(cartItem));
        // 模拟商品查询
        when(shangpinxinxiDao.selectById(1001L)).thenReturn(product);
        // 模拟原子扣减成功（返回受影响行数=1）
        when(shangpinxinxiDao.deductStock(eq(1001L), eq(2))).thenReturn(1);
        when(cangchuxinxiDao.deductStock(eq("SP001"), eq(2))).thenReturn(1);
        // 模拟订单插入
        when(ordersDao.insert(any(OrdersEntity.class))).thenReturn(1);
        // 模拟购物车删除
        when(cartDao.deleteBatchIds(anyList())).thenReturn(1);

        String orderId = ordersService.checkoutFromCart(100L, "测试地址", "13800000000", "张三", "备注");

        assertNotNull(orderId, "订单号不应为空");
        // 验证商品库存被扣减
        verify(shangpinxinxiDao).deductStock(1001L, 2);
        // 验证仓储台账被扣减
        verify(cangchuxinxiDao).deductStock("SP001", 2);
        // 验证订单被创建
        verify(ordersDao).insert(any(OrdersEntity.class));
        // 验证购物车被清空
        verify(cartDao).deleteBatchIds(anyList());
    }

    @Test
    public void testCheckoutFromCart_NullUserId() {
        assertThrows(EIException.class, () -> {
            ordersService.checkoutFromCart(null, "地址", "电话", "收货人", "备注");
        });
    }

    @Test
    public void testCheckoutFromCart_EmptyCart() {
        when(cartDao.selectList(any(EntityWrapper.class))).thenReturn(new ArrayList<>());
        assertThrows(EIException.class, () -> {
            ordersService.checkoutFromCart(100L, "地址", "电话", "收货人", "备注");
        });
    }

    @Test
    public void testCheckoutFromCart_ProductOutOfStock() {
        when(cartDao.selectList(any(EntityWrapper.class))).thenReturn(Arrays.asList(cartItem));
        when(shangpinxinxiDao.selectById(1001L)).thenReturn(product);
        // 模拟原子扣减失败（库存不足，返回0）
        when(shangpinxinxiDao.deductStock(eq(1001L), eq(2))).thenReturn(0);

        EIException ex = assertThrows(EIException.class, () -> {
            ordersService.checkoutFromCart(100L, "地址", "电话", "收货人", "备注");
        });
        assertTrue(ex.getMsg().contains("库存不足"), "异常信息应包含'库存不足'");
        // 验证仓储未被扣减
        verify(cangchuxinxiDao, never()).deductStock(anyString(), anyInt());
        // 验证订单未创建
        verify(ordersDao, never()).insert(any(OrdersEntity.class));
    }

    @Test
    public void testCheckoutFromCart_WarehouseOutOfStock_RollbackProductStock() {
        when(cartDao.selectList(any(EntityWrapper.class))).thenReturn(Arrays.asList(cartItem));
        when(shangpinxinxiDao.selectById(1001L)).thenReturn(product);
        // 商品扣减成功
        when(shangpinxinxiDao.deductStock(eq(1001L), eq(2))).thenReturn(1);
        // 仓储扣减失败
        when(cangchuxinxiDao.deductStock(eq("SP001"), eq(2))).thenReturn(0);

        EIException ex = assertThrows(EIException.class, () -> {
            ordersService.checkoutFromCart(100L, "地址", "电话", "收货人", "备注");
        });
        assertTrue(ex.getMsg().contains("仓储台账不足"), "异常信息应包含'仓储台账不足'");
        // 验证商品库存被回滚
        verify(shangpinxinxiDao).restoreStock(1001L, 2);
        // 验证订单未创建
        verify(ordersDao, never()).insert(any(OrdersEntity.class));
    }

    @Test
    public void testCheckoutFromCart_ExceedPerPersonLimit() {
        cartItem.setBuynumber(15); // 超过 onelimittimes=10
        when(cartDao.selectList(any(EntityWrapper.class))).thenReturn(Arrays.asList(cartItem));
        when(shangpinxinxiDao.selectById(1001L)).thenReturn(product);

        EIException ex = assertThrows(EIException.class, () -> {
            ordersService.checkoutFromCart(100L, "地址", "电话", "收货人", "备注");
        });
        assertTrue(ex.getMsg().contains("限购"), "异常信息应包含'限购'");
        // 验证库存未扣减
        verify(shangpinxinxiDao, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    public void testCheckoutFromCart_ProductNotFound() {
        when(cartDao.selectList(any(EntityWrapper.class))).thenReturn(Arrays.asList(cartItem));
        when(shangpinxinxiDao.selectById(1001L)).thenReturn(null);

        EIException ex = assertThrows(EIException.class, () -> {
            ordersService.checkoutFromCart(100L, "地址", "电话", "收货人", "备注");
        });
        assertTrue(ex.getMsg().contains("不存在"), "异常信息应包含'不存在'");
    }

    @Test
    public void testCheckoutFromCart_MultipleCartItems() {
        // 第二个购物车项
        CartEntity cartItem2 = new CartEntity();
        cartItem2.setId(2002L);
        cartItem2.setUserid(100L);
        cartItem2.setGoodid(1002L);
        cartItem2.setGoodname("测试雪碧");
        cartItem2.setBuynumber(1);
        cartItem2.setPrice(4.0f);

        ShangpinxinxiEntity product2 = new ShangpinxinxiEntity();
        product2.setId(1002L);
        product2.setShangpinbianhao("SP002");
        product2.setShangpinmingcheng("测试雪碧");
        product2.setAlllimittimes(30);
        product2.setPrice(4.0f);

        when(cartDao.selectList(any(EntityWrapper.class))).thenReturn(Arrays.asList(cartItem, cartItem2));
        when(shangpinxinxiDao.selectById(1001L)).thenReturn(product);
        when(shangpinxinxiDao.selectById(1002L)).thenReturn(product2);
        when(shangpinxinxiDao.deductStock(anyLong(), anyInt())).thenReturn(1);
        when(cangchuxinxiDao.deductStock(anyString(), anyInt())).thenReturn(1);
        when(ordersDao.insert(any(OrdersEntity.class))).thenReturn(1);
        when(cartDao.deleteBatchIds(anyList())).thenReturn(2);

        String orderId = ordersService.checkoutFromCart(100L, "地址", "电话", "张三", "");

        assertNotNull(orderId);
        // 两次商品扣减
        verify(shangpinxinxiDao, times(2)).deductStock(anyLong(), anyInt());
        verify(cangchuxinxiDao, times(2)).deductStock(anyString(), anyInt());
        // 两个订单
        verify(ordersDao, times(2)).insert(any(OrdersEntity.class));
    }

    // ========================= updateOrderStatus 测试 =========================

    @Test
    public void testUpdateOrderStatus_UnpaidToPaid_Success() {
        OrdersEntity order = new OrdersEntity();
        order.setId(1L);
        order.setStatus("待支付");
        order.setUserid(100L);
        order.setTotal(10.0f);
        order.setGoodid(1001L);
        order.setBuynumber(2);

        when(ordersDao.selectById(1L)).thenReturn(order);
        when(yonghuDao.selectById(100L)).thenReturn(user);
        when(yonghuDao.updateById(any(YonghuEntity.class))).thenReturn(1);
        when(ordersDao.updateById(any(OrdersEntity.class))).thenReturn(1);

        ordersService.updateOrderStatus(1L, "已支付");

        verify(ordersDao).updateById(argThat(o -> "已支付".equals(((OrdersEntity)o).getStatus())));
        // 验证余额被扣减
        verify(yonghuDao).updateById(any(YonghuEntity.class));
    }

    @Test
    public void testUpdateOrderStatus_UnpaidToCancelled_RestoreStock() {
        OrdersEntity order = new OrdersEntity();
        order.setId(1L);
        order.setStatus("待支付");
        order.setUserid(100L);
        order.setTotal(10.0f);
        order.setGoodid(1001L);
        order.setBuynumber(2);

        when(ordersDao.selectById(1L)).thenReturn(order);
        when(shangpinxinxiDao.selectById(1001L)).thenReturn(product);
        when(ordersDao.updateById(any(OrdersEntity.class))).thenReturn(1);

        ordersService.updateOrderStatus(1L, "已取消");

        // 验证库存被恢复
        verify(shangpinxinxiDao).restoreStock(1001L, 2);
        verify(cangchuxinxiDao).restoreStock("SP001", 2);
        verify(ordersDao).updateById(argThat(o -> "已取消".equals(((OrdersEntity)o).getStatus())));
    }

    @Test
    public void testUpdateOrderStatus_PaidToCompleted_Success() {
        OrdersEntity order = new OrdersEntity();
        order.setId(1L);
        order.setStatus("已支付");
        order.setGoodid(1001L);
        order.setBuynumber(2);

        when(ordersDao.selectById(1L)).thenReturn(order);
        when(ordersDao.updateById(any(OrdersEntity.class))).thenReturn(1);

        ordersService.updateOrderStatus(1L, "已完成");

        verify(ordersDao).updateById(argThat(o -> "已完成".equals(((OrdersEntity)o).getStatus())));
    }

    @Test
    public void testUpdateOrderStatus_IllegalTransition_CompletedToCancelled() {
        OrdersEntity order = new OrdersEntity();
        order.setId(1L);
        order.setStatus("已完成");

        when(ordersDao.selectById(1L)).thenReturn(order);

        EIException ex = assertThrows(EIException.class, () -> {
            ordersService.updateOrderStatus(1L, "已取消");
        });
        assertTrue(ex.getMsg().contains("非法状态跳转"));
    }

    @Test
    public void testUpdateOrderStatus_IllegalTransition_CancelledToPaid() {
        OrdersEntity order = new OrdersEntity();
        order.setId(1L);
        order.setStatus("已取消");

        when(ordersDao.selectById(1L)).thenReturn(order);

        assertThrows(EIException.class, () -> {
            ordersService.updateOrderStatus(1L, "已支付");
        });
    }

    @Test
    public void testUpdateOrderStatus_IllegalTransition_UnpaidToCompleted() {
        OrdersEntity order = new OrdersEntity();
        order.setId(1L);
        order.setStatus("待支付");

        when(ordersDao.selectById(1L)).thenReturn(order);

        assertThrows(EIException.class, () -> {
            ordersService.updateOrderStatus(1L, "已完成");
        });
    }

    @Test
    public void testUpdateOrderStatus_OrderNotFound() {
        when(ordersDao.selectById(999L)).thenReturn(null);

        assertThrows(EIException.class, () -> {
            ordersService.updateOrderStatus(999L, "已支付");
        });
    }

    @Test
    public void testUpdateOrderStatus_NullOrderId() {
        assertThrows(EIException.class, () -> {
            ordersService.updateOrderStatus(null, "已支付");
        });
    }

    @Test
    public void testUpdateOrderStatus_EmptyTargetStatus() {
        assertThrows(EIException.class, () -> {
            ordersService.updateOrderStatus(1L, "");
        });
    }

    @Test
    public void testUpdateOrderStatus_InsufficientBalance() {
        // 测试余额不足场景
        OrdersEntity order = new OrdersEntity();
        order.setId(1L);
        order.setStatus("待支付");
        order.setUserid(100L);
        order.setTotal(9999.0f); // 超过用户余额 1000
        order.setGoodid(1001L);
        order.setBuynumber(2);

        when(ordersDao.selectById(1L)).thenReturn(order);
        when(yonghuDao.selectById(100L)).thenReturn(user);

        EIException ex = assertThrows(EIException.class, () -> {
            ordersService.updateOrderStatus(1L, "已支付");
        });
        assertTrue(ex.getMsg().contains("余额不足"), "异常信息应包含'余额不足'");
        // 验证订单状态未变更
        verify(ordersDao, never()).updateById(any(OrdersEntity.class));
    }

    // ========================= recognizeAndAddToCart 测试 =========================

    @Test
    public void testRecognizeAndAddToCart_NullUserId() {
        assertThrows(EIException.class, () -> {
            ordersService.recognizeAndAddToCart(null, "/tmp/test.jpg", 1);
        });
    }

    @Test
    public void testRecognizeAndAddToCart_EmptyImagePath() {
        assertThrows(EIException.class, () -> {
            ordersService.recognizeAndAddToCart(100L, "", 1);
        });
    }

    @Test
    public void testRecognizeAndAddToCart_OcrFailure() {
        // mock OCR 返回 null，模拟识别失败
        when(baiduUtil.generalString(anyString(), anyBoolean())).thenReturn(null);

        EIException ex = assertThrows(EIException.class, () -> {
            ordersService.recognizeAndAddToCart(100L, "/nonexistent/path.jpg", 1);
        });
        // 应当给出明确的识别失败错误信息
        assertTrue(
            ex.getMsg().contains("识别"),
            "异常信息应当明确说明识别失败原因，实际: " + ex.getMsg()
        );
    }

    // ========================= 状态机覆盖测试 =========================

    @Test
    public void testStateMachine_AllValidTransitions() {
        // 测试所有合法跳转
        String[][] validTransitions = {
            {"待支付", "已支付"},
            {"待支付", "已取消"},
            {"已支付", "已发货"},
            {"已支付", "已完成"},
            {"已发货", "已完成"}
        };

        for (String[] transition : validTransitions) {
            OrdersEntity order = new OrdersEntity();
            order.setId(1L);
            order.setStatus(transition[0]);
            order.setUserid(100L);
            order.setTotal(10.0f);
            order.setGoodid(1001L);
            order.setBuynumber(2);

            when(ordersDao.selectById(1L)).thenReturn(order);
            when(ordersDao.updateById(any(OrdersEntity.class))).thenReturn(1);
            if ("已支付".equals(transition[1]) && "待支付".equals(transition[0])) {
                when(yonghuDao.selectById(100L)).thenReturn(user);
                when(yonghuDao.updateById(any(YonghuEntity.class))).thenReturn(1);
            }
            if ("已取消".equals(transition[1])) {
                when(shangpinxinxiDao.selectById(1001L)).thenReturn(product);
            }

            ordersService.updateOrderStatus(1L, transition[1]);
            verify(ordersDao).updateById(argThat(o ->
                transition[1].equals(((OrdersEntity)o).getStatus())));

            // Reset mocks for next iteration
            reset(ordersDao, shangpinxinxiDao, cangchuxinxiDao, yonghuDao);
        }
    }

    @Test
    public void testStateMachine_AllInvalidTransitions() {
        // 测试所有非法跳转
        String[][] invalidTransitions = {
            {"已完成", "已支付"},
            {"已完成", "已取消"},
            {"已取消", "已支付"},
            {"已取消", "已完成"},
            {"已发货", "已取消"},
            {"已发货", "待支付"},
            {"已支付", "待支付"},
            {"待支付", "已发货"}
        };

        for (String[] transition : invalidTransitions) {
            OrdersEntity order = new OrdersEntity();
            order.setId(1L);
            order.setStatus(transition[0]);

            when(ordersDao.selectById(1L)).thenReturn(order);

            EIException ex = assertThrows(EIException.class, () -> {
                ordersService.updateOrderStatus(1L, transition[1]);
            }, "从 [" + transition[0] + "] 到 [" + transition[1] + "] 应当被拒绝");
            assertTrue(ex.getMsg().contains("非法状态跳转"),
                "异常应包含'非法状态跳转'，实际: " + ex.getMsg());

            reset(ordersDao);
        }
    }
}
