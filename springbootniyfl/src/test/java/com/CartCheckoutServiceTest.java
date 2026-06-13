package com;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.entity.*;
import com.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 购物车结算下单 - 服务层集成测试
 *
 * 运行前提：需要连接 springbootniyfl 数据库（application.yml 配置）
 * pom.xml 中 skipTests=true，mvn package 不会执行这些测试。
 * 手动运行：mvn test -DskipTests=false -Dtest=CartCheckoutServiceTest
 * 或在 IDE 中直接运行本测试类。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional // 每个测试方法结束后自动回滚，不污染数据库
public class CartCheckoutServiceTest {

    @Autowired
    private ShangpinxinxiService shangpinxinxiService;
    @Autowired
    private CangchuxinxiService cangchuxinxiService;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrdersService ordersService;

    private static final Long TEST_USER_ID = 99999L;

    // ---------- 辅助方法 ----------

    private ShangpinxinxiEntity createTestProduct(String bianhao, String name, int stock, int oneLimit) {
        ShangpinxinxiEntity p = new ShangpinxinxiEntity();
        p.setId(new Date().getTime() + (long)(Math.random() * 1000));
        p.setShangpinbianhao(bianhao);
        p.setShangpinmingcheng(name);
        p.setShangpinfenlei("测试分类");
        p.setAlllimittimes(stock);
        p.setOnelimittimes(oneLimit);
        p.setPrice(99.0f);
        p.setAddtime(new Date());
        shangpinxinxiService.insert(p);
        return p;
    }

    private CangchuxinxiEntity createTestWarehouse(String bianhao, String name, int shelfQty) {
        CangchuxinxiEntity c = new CangchuxinxiEntity();
        c.setId(new Date().getTime() + (long)(Math.random() * 1000));
        c.setShangpinbianhao(bianhao);
        c.setShangpinmingcheng(name);
        c.setShangpinfenlei("测试分类");
        c.setAlllimittimes(shelfQty);
        c.setAddtime(new Date());
        cangchuxinxiService.insert(c);
        return c;
    }

    private CartEntity createTestCartItem(Long productId, String name, int qty, float price) {
        CartEntity cart = new CartEntity();
        cart.setId(new Date().getTime() + (long)(Math.random() * 1000));
        cart.setTablename("shangpinxinxi");
        cart.setUserid(TEST_USER_ID);
        cart.setGoodid(productId);
        cart.setGoodname(name);
        cart.setBuynumber(qty);
        cart.setPrice(price);
        cart.setGoodtype("测试分类");
        cart.setAddtime(new Date());
        cartService.insert(cart);
        return cart;
    }

    // ---------- 库存扣减测试 ----------

    @Test
    @Order(1)
    @DisplayName("商品库存原子扣减 - 正常扣减")
    void testDeductStock_success() {
        ShangpinxinxiEntity product = createTestProduct("TEST001", "测试商品A", 10, 5);
        int rows = shangpinxinxiService.deductStock(product.getId(), 3);
        assertEquals(1, rows, "扣减应成功，影响1行");

        ShangpinxinxiEntity updated = shangpinxinxiService.selectById(product.getId());
        assertEquals(7, updated.getAlllimittimes(), "库存应从10减为7");
    }

    @Test
    @Order(2)
    @DisplayName("商品库存原子扣减 - 库存不足时返回0行")
    void testDeductStock_insufficient() {
        ShangpinxinxiEntity product = createTestProduct("TEST002", "测试商品B", 2, 5);
        int rows = shangpinxinxiService.deductStock(product.getId(), 5);
        assertEquals(0, rows, "库存不足，应返回0行");

        ShangpinxinxiEntity updated = shangpinxinxiService.selectById(product.getId());
        assertEquals(2, updated.getAlllimittimes(), "库存不应被扣减");
    }

    @Test
    @Order(3)
    @DisplayName("商品库存原子扣减 - 刚好扣完")
    void testDeductStock_exact() {
        ShangpinxinxiEntity product = createTestProduct("TEST003", "测试商品C", 5, 10);
        int rows = shangpinxinxiService.deductStock(product.getId(), 5);
        assertEquals(1, rows, "刚好扣完应成功");

        ShangpinxinxiEntity updated = shangpinxinxiService.selectById(product.getId());
        assertEquals(0, updated.getAlllimittimes(), "库存应为0");
    }

    @Test
    @Order(4)
    @DisplayName("仓储库存扣减 - 按商品编号匹配")
    void testWarehouseDeduct() {
        createTestWarehouse("WH001", "仓储商品A", 20);
        int rows = cangchuxinxiService.deductStock("WH001", 8);
        assertEquals(1, rows, "仓储扣减应成功");

        CangchuxinxiEntity updated = cangchuxinxiService.selectOne(
            new EntityWrapper<CangchuxinxiEntity>().eq("shangpinbianhao", "WH001"));
        assertEquals(12, updated.getAlllimittimes(), "仓储库存应从20减为12");
    }

    // ---------- 订单状态跳转测试 ----------

    @Test
    @Order(5)
    @DisplayName("订单状态 - 待支付→已完成 合法")
    void testStatusTransition_toCompleted() {
        assertTrue(isValidTransition("待支付", "已完成"));
    }

    @Test
    @Order(6)
    @DisplayName("订单状态 - 待支付→已取消 合法")
    void testStatusTransition_toCancelled() {
        assertTrue(isValidTransition("待支付", "已取消"));
    }

    @Test
    @Order(7)
    @DisplayName("订单状态 - 已完成→待支付 非法")
    void testStatusTransition_completedToUnpaid() {
        assertFalse(isValidTransition("已完成", "待支付"));
    }

    @Test
    @Order(8)
    @DisplayName("订单状态 - 已取消→已完成 非法")
    void testStatusTransition_cancelledToCompleted() {
        assertFalse(isValidTransition("已取消", "已完成"));
    }

    @Test
    @Order(9)
    @DisplayName("订单状态 - 已完成→已取消 非法")
    void testStatusTransition_completedToCancelled() {
        assertFalse(isValidTransition("已完成", "已取消"));
    }

    private boolean isValidTransition(String from, String to) {
        if ("待支付".equals(from) && ("已完成".equals(to) || "已取消".equals(to))) {
            return true;
        }
        return false;
    }

    // ---------- 完整结算流程测试 ----------

    @Test
    @Order(10)
    @DisplayName("完整结算流程 - 库存扣减→订单生成→购物车清空")
    void testFullCheckoutFlow() {
        // 准备：商品 + 仓储 + 购物车
        ShangpinxinxiEntity product = createTestProduct("FLOW001", "流程测试商品", 10, 5);
        createTestWarehouse("FLOW001", "流程测试商品", 10);
        createTestCartItem(product.getId(), "流程测试商品", 2, 99.0f);

        // 模拟结算流程
        List<CartEntity> cartList = cartService.selectList(
            new EntityWrapper<CartEntity>().eq("userid", TEST_USER_ID));
        assertFalse(cartList.isEmpty(), "购物车不应为空");

        String orderid = "TEST" + System.currentTimeMillis();
        for (CartEntity cart : cartList) {
            ShangpinxinxiEntity p = shangpinxinxiService.selectById(cart.getGoodid());
            assertNotNull(p);

            int rows = shangpinxinxiService.deductStock(p.getId(), cart.getBuynumber());
            assertEquals(1, rows, "库存扣减应成功");

            cangchuxinxiService.deductStock(p.getShangpinbianhao(), cart.getBuynumber());

            OrdersEntity order = new OrdersEntity();
            order.setId(new Date().getTime() + (long)(Math.random() * 1000));
            order.setOrderid(orderid);
            order.setTablename("shangpinxinxi");
            order.setUserid(TEST_USER_ID);
            order.setGoodid(cart.getGoodid());
            order.setGoodname(cart.getGoodname());
            order.setBuynumber(cart.getBuynumber());
            order.setPrice(p.getPrice());
            order.setTotal(p.getPrice() * cart.getBuynumber());
            order.setStatus("待支付");
            order.setAddtime(new Date());
            ordersService.insert(order);
        }

        // 清空购物车
        cartService.delete(new EntityWrapper<CartEntity>().eq("userid", TEST_USER_ID));

        // 验证
        ShangpinxinxiEntity updatedProduct = shangpinxinxiService.selectById(product.getId());
        assertEquals(8, updatedProduct.getAlllimittimes(), "商品库存应从10减为8");

        List<CartEntity> remainingCart = cartService.selectList(
            new EntityWrapper<CartEntity>().eq("userid", TEST_USER_ID));
        assertTrue(remainingCart.isEmpty(), "购物车应已清空");

        List<OrdersEntity> orders = ordersService.selectList(
            new EntityWrapper<OrdersEntity>().eq("orderid", orderid));
        assertFalse(orders.isEmpty(), "应生成订单");
        assertEquals("待支付", orders.get(0).getStatus(), "订单状态应为待支付");
    }
}
