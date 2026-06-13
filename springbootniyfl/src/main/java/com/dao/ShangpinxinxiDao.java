package com.dao;

import com.entity.ShangpinxinxiEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.ShangpinxinxiVO;
import com.entity.view.ShangpinxinxiView;


/**
 * 商品信息
 *
 * @author
 * @email
 * @date 2023-05-18 15:40:06
 */
public interface ShangpinxinxiDao extends BaseMapper<ShangpinxinxiEntity> {

	List<ShangpinxinxiVO> selectListVO(@Param("ew") Wrapper<ShangpinxinxiEntity> wrapper);

	ShangpinxinxiVO selectVO(@Param("ew") Wrapper<ShangpinxinxiEntity> wrapper);

	List<ShangpinxinxiView> selectListView(@Param("ew") Wrapper<ShangpinxinxiEntity> wrapper);

	List<ShangpinxinxiView> selectListView(Pagination page,@Param("ew") Wrapper<ShangpinxinxiEntity> wrapper);

	ShangpinxinxiView selectView(@Param("ew") Wrapper<ShangpinxinxiEntity> wrapper);

	/**
	 * 原子扣减库存（防超卖）：UPDATE SET alllimittimes = alllimittimes - quantity WHERE id = ? AND alllimittimes >= quantity
	 * @return 受影响行数，0 表示库存不足
	 */
	int deductStock(@Param("id") Long id, @Param("quantity") int quantity);

	/**
	 * 恢复库存（订单取消时回滚）
	 */
	int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);

}
