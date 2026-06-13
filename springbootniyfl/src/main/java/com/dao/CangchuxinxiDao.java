package com.dao;

import com.entity.CangchuxinxiEntity;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.baomidou.mybatisplus.plugins.pagination.Pagination;

import org.apache.ibatis.annotations.Param;
import com.entity.vo.CangchuxinxiVO;
import com.entity.view.CangchuxinxiView;


/**
 * 仓储信息
 *
 * @author
 * @email
 * @date 2023-05-18 15:40:06
 */
public interface CangchuxinxiDao extends BaseMapper<CangchuxinxiEntity> {

	List<CangchuxinxiVO> selectListVO(@Param("ew") Wrapper<CangchuxinxiEntity> wrapper);

	CangchuxinxiVO selectVO(@Param("ew") Wrapper<CangchuxinxiEntity> wrapper);

	List<CangchuxinxiView> selectListView(@Param("ew") Wrapper<CangchuxinxiEntity> wrapper);

	List<CangchuxinxiView> selectListView(Pagination page,@Param("ew") Wrapper<CangchuxinxiEntity> wrapper);

	CangchuxinxiView selectView(@Param("ew") Wrapper<CangchuxinxiEntity> wrapper);

	/**
	 * 原子扣减仓储上架数量（防超卖）
	 * @return 受影响行数，0 表示库存不足
	 */
	int deductStock(@Param("shangpinbianhao") String shangpinbianhao, @Param("quantity") int quantity);

	/**
	 * 恢复仓储上架数量（订单取消时回滚）
	 */
	int restoreStock(@Param("shangpinbianhao") String shangpinbianhao, @Param("quantity") int quantity);

}
