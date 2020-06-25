package io.xxnjdg.mall.product.service.impl;

import io.xxnjdg.common.utils.R;
import io.xxnjdg.common.utils.ServiceExceptionUtil;
import io.xxnjdg.mall.coupon.entity.SpuBoundsEntity;
import io.xxnjdg.mall.coupon.service.SkuFullReductionService;
import io.xxnjdg.mall.coupon.service.SpuBoundsService;
import io.xxnjdg.mall.product.constant.ProductConstant;
import io.xxnjdg.mall.product.constant.ProductErrorCodeEnum;
import io.xxnjdg.mall.product.entity.*;
import io.xxnjdg.mall.product.service.*;
import io.xxnjdg.mall.product.vo.*;
import io.xxnjdg.mall.search.po.Attrs;
import io.xxnjdg.mall.search.po.SkuEsModel;
import io.xxnjdg.mall.search.service.ProductSaveService;
import io.xxnjdg.mall.ware.service.WareSkuService;
import io.xxnjdg.mall.ware.vo.SkuHasStockVO;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.xxnjdg.common.utils.PageUtils;
import io.xxnjdg.common.utils.Query;

import io.xxnjdg.mall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;


@Service("spuInfoService")
@org.apache.dubbo.config.annotation.Service(protocol = "dubbo", version = "1.0.0", validation = "true")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    private SpuInfoDescService spuInfoDescService;

    @Autowired
    private SpuImagesService imagesService;

    @Autowired
    private AttrService attrService;

    @Autowired
    private ProductAttrValueService attrValueService;

    @Autowired
    private SkuInfoService skuInfoService;
    @Autowired
    private SkuImagesService skuImagesService;

    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private CategoryService categoryService;

    @Reference(version = "1.0.0", validation = "true")
    private SpuBoundsService spuBoundsService;

    @Reference(version = "1.0.0", validation = "true")
    private SkuFullReductionService skuFullReductionService;

    @Reference(version = "1.0.0", validation = "true")
    private WareSkuService wareSkuService;

    @Reference(version = "1.0.0", validation = "true")
    private ProductSaveService productSaveService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * //TODO 高级部分完善
     *
     * @param vo
     */
    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {

        //1、保存spu基本信息 pms_spu_info
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(vo, infoEntity);
        infoEntity.setCreateTime(new Date());
        infoEntity.setUpdateTime(new Date());
        this.saveBaseSpuInfo(infoEntity);

        //2、保存Spu的描述图片 pms_spu_info_desc
        List<String> decript = vo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        descEntity.setSpuId(infoEntity.getId());
        descEntity.setDecript(String.join(",", decript));
        spuInfoDescService.saveSpuInfoDesc(descEntity);


        //3、保存spu的图片集 pms_spu_images
        List<String> images = vo.getImages();
        imagesService.saveImages(infoEntity.getId(), images);


        //4、保存spu的规格参数;pms_product_attr_value
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        List<ProductAttrValueEntity> collect = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
            valueEntity.setAttrId(attr.getAttrId());
            AttrEntity id = attrService.getById(attr.getAttrId());
            valueEntity.setAttrName(id.getAttrName());
            valueEntity.setAttrValue(attr.getAttrValues());
            valueEntity.setQuickShow(attr.getShowDesc());
            valueEntity.setSpuId(infoEntity.getId());

            return valueEntity;
        }).collect(Collectors.toList());
        attrValueService.saveProductAttr(collect);


        //5、保存spu的积分信息；gulimall_sms->sms_spu_bounds
        Bounds bounds = vo.getBounds();
//        SpuBoundTo spuBoundTo = new SpuBoundTo();
//        BeanUtils.copyProperties(bounds,spuBoundTo);
//        spuBoundTo.setSpuId(infoEntity.getId());
//        R r = spuBoundsService.saveSpuBounds(spuBoundTo);
//        if(r.getCode() != 0){
//            log.error("远程保存spu积分信息失败");
//        }

        SpuBoundsEntity spuBoundsEntity = new SpuBoundsEntity();
        BeanUtils.copyProperties(bounds, spuBoundsEntity);
        spuBoundsEntity.setSpuId(infoEntity.getId());
        boolean save = spuBoundsService.save(spuBoundsEntity);
        if (!save) {
            log.error("远程保存spu积分信息失败");
        }

        //5、保存当前spu对应的所有sku信息；

        List<Skus> skus = vo.getSkus();
        if (skus != null && skus.size() > 0) {
            skus.forEach(item -> {
                String defaultImg = "";
                for (Images image : item.getImages()) {
                    if (image.getDefaultImg() == 1) {
                        defaultImg = image.getImgUrl();
                    }
                }
                //    private String skuName;
                //    private BigDecimal price;
                //    private String skuTitle;
                //    private String skuSubtitle;
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(item, skuInfoEntity);
                skuInfoEntity.setBrandId(infoEntity.getBrandId());
                skuInfoEntity.setCatalogId(infoEntity.getCatalogId());
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSpuId(infoEntity.getId());
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                //5.1）、sku的基本信息；pms_sku_info
                skuInfoService.saveSkuInfo(skuInfoEntity);

                Long skuId = skuInfoEntity.getSkuId();

                List<SkuImagesEntity> imagesEntities = item.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity -> {
                    //返回true就是需要，false就是剔除
                    return !StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                //5.2）、sku的图片信息；pms_sku_image
                skuImagesService.saveBatch(imagesEntities);
                //TODO 没有图片路径的无需保存

                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    SkuSaleAttrValueEntity attrValueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(a, attrValueEntity);
                    attrValueEntity.setSkuId(skuId);

                    return attrValueEntity;
                }).collect(Collectors.toList());
                //5.3）、sku的销售属性信息：pms_sku_sale_attr_value
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);

                // //5.4）、sku的优惠、满减等信息；gulimall_sms->sms_sku_ladder\sms_sku_full_reduction\sms_member_price
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(item, skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                if (skuReductionTo.getFullCount() > 0 || skuReductionTo.getFullPrice().compareTo(new BigDecimal("0")) == 1) {
//                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
//                    if(r1.getCode() != 0){
//                        log.error("远程保存sku优惠信息失败");
//                    }
                    skuFullReductionService.saveSkuReduction(skuReductionTo);
                }

            });
        }

    }

    @Override
    public void saveBaseSpuInfo(SpuInfoEntity infoEntity) {
        this.baseMapper.insert(infoEntity);
    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {

        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            wrapper.and((w) -> {
                w.eq("id", key).or().like("spu_name", key);
            });
        }
        // status=1 and (id=1 or spu_name like xxx)
        String status = (String) params.get("status");
        if (!StringUtils.isEmpty(status)) {
            wrapper.eq("publish_status", status);
        }

        String brandId = (String) params.get("brandId");
        if (!StringUtils.isEmpty(brandId) && !"0".equalsIgnoreCase(brandId)) {
            wrapper.eq("brand_id", brandId);
        }

        String catelogId = (String) params.get("catelogId");
        if (!StringUtils.isEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)) {
            wrapper.eq("catalog_id", catelogId);
        }

        /**
         * status: 2
         * key:
         * brandId: 9
         * catelogId: 225
         */

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Override
    @Transactional
    public void up(Long spuId) {

        // 1 查出当前 spuId 对应的所有 sku 信息，品牌的名字
        List<SkuInfoEntity> skuInfoEntities = skuInfoService.getSkusBySpuId(spuId);

        List<Long> skuIdList = skuInfoEntities.stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());

        // TODO 4 查询当前 sku 的所有可以用来被检索的规格属性
        List<ProductAttrValueEntity> baseAttrs = attrValueService.baseAttrlistforspu(spuId);
        List<Long> attrIds = baseAttrs.stream().map(attr -> attr.getAttrId()).collect(Collectors.toList());

        List<Long> searchAttrIds = attrService.selectSearchAttrs(attrIds);

        Set<Long> idSet = new HashSet<>(searchAttrIds);

        List<Attrs> attrsList = baseAttrs.stream().filter(o -> idSet.contains(o.getAttrId()))
                .map(o -> {
                    Attrs attrs1 = new Attrs();
                    BeanUtils.copyProperties(o, attrs1);
                    return attrs1;
                }).collect(Collectors.toList());

        // TODO 1 发送远程调用 在仓库系统中查询是否有库存
        Map<Long, Boolean> stockMap = null;
        try {
            List<SkuHasStockVO> skusHasStock = wareSkuService.getSkusHasStock(skuIdList);
            if (!CollectionUtils.isEmpty(skusHasStock)) {
                stockMap = skusHasStock.stream().collect(Collectors.toMap(SkuHasStockVO::getSkuId, SkuHasStockVO::getHasStock));
            }
        } catch (Exception e) {
            log.error("库存服务查询异常，原因：", e);
        }

        // 2 封装每个 sku 的信息
        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> upProducts = skuInfoEntities.stream()
                .map(skuInfoEntity -> {
                    // 组装需要的数据
                    SkuEsModel esSkuModel = new SkuEsModel();
                    BeanUtils.copyProperties(skuInfoEntity, esSkuModel);
                    // 手动设置 field 不一致的名称的值
                    // skuPrice -> price skuImg -> skuDefaultImg
                    esSkuModel.setSkuPrice(skuInfoEntity.getPrice());
                    esSkuModel.setSkuImg(skuInfoEntity.getSkuDefaultImg());
                    // hasStock hotScore

                    // 设置库存信息
                    if (finalStockMap == null) {
                        esSkuModel.setHasStock(true);
                    } else {
                        esSkuModel.setHasStock(finalStockMap.get(skuInfoEntity.getSkuId()));
                    }

                    // TODO 2 热度评分 0
                    esSkuModel.setHotScore(0L);

                    // TODO 3 查询品牌和分类的名字信息
                    BrandEntity brandEntity = brandService.getById(esSkuModel.getBrandId());
                    esSkuModel.setBrandName(brandEntity.getName());
                    esSkuModel.setBrandImg(brandEntity.getLogo());
                    CategoryEntity categoryEntity = categoryService.getById(esSkuModel.getCatalogId());
                    esSkuModel.setCatalogName(categoryEntity.getName());

                    // 设置检索属性
                    esSkuModel.setAttrs(attrsList);

                    return esSkuModel;
                }).collect(Collectors.toList());

        // TODO 5 将数据发送给 es 进行保存 mall-search

        // TODO 6 修改SPU状态
        baseMapper.updateSpuStatus(spuId, ProductConstant.StatusEnum.SPU_UP.getCode());
        /**
         * Feign 调用流程
         * 1. 构造请求数据 将对象转为 JSON
         *      RequestTemplate
         * 2. 发送请求进行之星(执行成功会解码相应数据)
         *     executeAndDecode(template)
         * 3. 执行请求会有重试机制
         */
        boolean b = productSaveService.productStatusUp(upProducts);
        if (b) {
            // 上架失败
            // TODO 7 幂等性问题
            throw ServiceExceptionUtil.exception(ProductErrorCodeEnum.TEST_EXISTS.getCode());
        }

    }

}