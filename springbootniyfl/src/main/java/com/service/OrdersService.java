package com.service;

import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.entity.OrdersEntity;
import java.util.List;
import java.util.Map;
import com.entity.vo.OrdersVO;
import org.apache.ibatis.annotations.Param;
import com.entity.view.OrdersView;


/**
 * 订单
 *
 * @author
 * @email
 * @date 2023-05-18 15:40:06
 */
public interface OrdersService extends IService<OrdersEntity> {

    PageUtils queryPage(Map<String, Object> params);

   	List<OrdersVO> selectListVO(Wrapper<OrdersEntity> wrapper);

   	OrdersVO selectVO(@Param("ew") Wrapper<OrdersEntity> wrapper);

   	List<OrdersView> selectListView(Wrapper<OrdersEntity> wrapper);

   	OrdersView selectView(@Param("ew") Wrapper<OrdersEntity> wrapper);

   	PageUtils queryPage(Map<String, Object> params,Wrapper<OrdersEntity> wrapper);


    List<Map<String, Object>> selectValue(Map<String, Object> params,Wrapper<OrdersEntity> wrapper);

    List<Map<String, Object>> selectTimeStatValue(Map<String, Object> params,Wrapper<OrdersEntity> wrapper);

    List<Map<String, Object>> selectGroup(Map<String, Object> params,Wrapper<OrdersEntity> wrapper);

    /**
     * 从购物车结算下单（事务保证原子性）
     * 1. 查询购物车商品
     * 2. 校验库存（商品库存 + 仓储台账）
     * 3. 原子扣减商品库存（防超卖）
     * 4. 原子扣减仓储台账
     * 5. 生成订单（待支付状态）
     * 6. 清空用户购物车
     *
     * @param userId    当前登录用户ID
     * @param address   收货地址
     * @param tel       联系电话
     * @param consignee 收货人
     * @param remark    备注
     * @return 生成的订单编号 orderid
     */
    String checkoutFromCart(Long userId, String address, String tel, String consignee, String remark);

    /**
     * 订单状态流转（带状态机校验，拒绝非法跳转）
     * 合法状态：待支付 -> 已支付/已取消；已支付 -> 已完成
     *
     * @param orderId      订单记录ID
     * @param targetStatus 目标状态
     */
    void updateOrderStatus(Long orderId, String targetStatus);

    /**
     * 扫码/拍照识别商品并加入购物车
     * 使用百度OCR识别图片文字，匹配商品名称或商品编号
     *
     * @param userId    用户ID
     * @param imagePath 服务器上的图片绝对路径
     * @param quantity  加购数量（默认1）
     * @return 匹配到的商品信息（用于前端展示），识别失败返回null
     */
    Map<String, Object> recognizeAndAddToCart(Long userId, String imagePath, int quantity);

}
