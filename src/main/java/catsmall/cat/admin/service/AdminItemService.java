package catsmall.cat.admin.service;

import catsmall.cat.admin.manage.dto.ModifyItemDto;
import catsmall.cat.admin.repository.AdminCategoryRepository;
import catsmall.cat.admin.repository.AdminItemCategoryRepository;
import catsmall.cat.admin.repository.AdminItemRepository;
import catsmall.cat.alert.AlertUtils;
import catsmall.cat.config.AppProperties;
import catsmall.cat.entity.Category;
import catsmall.cat.entity.ItemCategory;
import catsmall.cat.entity.dto.ItemTypeDto;
import catsmall.cat.entity.dto.item.ItemCategoryDto;
import catsmall.cat.entity.dto.item.ItemDto;
import catsmall.cat.entity.item.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminItemService {
    private final AdminItemRepository adminItemRepository;
    private final AdminCategoryRepository adminCategoryRepository;
    private final AdminItemCategoryRepository adminItemCategoryRepository;
    private final ModelMapper modelMapper;
    private final AlertUtils alertUtils;
    private final AppProperties appProperties;

    public ItemCategoryDto findAllCategoryAndType() {
        List<ItemCategory> result = adminItemRepository.findAllCategoryAndTypes();
        ItemCategoryDto itemCategoryDto = ItemCategoryDto.bindingItemCategory(result);
        return itemCategoryDto;
    }

    public ItemCategoryDto findTypesByCategoryName(String category) {
        List<ItemCategory> result = adminItemRepository.findTypesByCategoryName(category);
        ItemCategoryDto itemCategoryDto = ItemCategoryDto.bindingItemCategory(result);
        return itemCategoryDto;
    }

    @Transactional
    public void addType(ItemTypeDto itemTypeDto) {
        Item item = itemTypeDto.selectItemByType(itemTypeDto.getCategoryName(), itemTypeDto.getType());
        List<ItemCategory> itemCategories = adminItemRepository.findTypesByCategoryName(itemTypeDto.getCategoryName());
        Category category = itemCategories.get(0).getCategory();
        ItemCategory itemCategory = new ItemCategory(category, item);
        adminItemRepository.save(item);
        adminCategoryRepository.save(category);
    }

    @Transactional
    public void addItem(ItemDto itemDto, BindingResult result) {
        try {
            itemDto.setThumbnailName(saveThumbnail(itemDto.getThumbnail()));
            itemDto.setThumbnail_origin(itemDto.getThumbnail().getOriginalFilename());
        } catch (IOException e) {
            result.rejectValue("thumbnail", null, "????????? ????????? ????????? ???????????????.");
        }
        String itemName = itemDto.getName();
        for(int a=0; a < 100; a++) {
            Item item = itemDto.transformItem(itemDto);
            item.setName(itemName + a);
            adminItemRepository.save(item);
            Category category = adminCategoryRepository.findByName(itemDto.getCategory());
            ItemCategory itemCategory = new ItemCategory(category, item);
            adminItemCategoryRepository.save(itemCategory);
        }
    }

    // TODO ?????? ?????? ?????? yml????????? ??????????????? ????????????
    private String saveThumbnail(MultipartFile thumbnail) throws IOException {
        int index = thumbnail.getContentType().indexOf('/');
        if (!thumbnail.getContentType().substring(0, index).equals("image")) {
            throw new IOException();
        }
        File thumbDirectory = new File(appProperties.getImgPrefix() + "thumbnail/");
        if (!thumbDirectory.exists()) {
            thumbDirectory.mkdirs();
        }
        String extension = FilenameUtils.getExtension(thumbnail.getOriginalFilename()); // . ????????? ???????????? ?????? ex) .jpg = jpg??????
        String savedFileName = UUID.randomUUID().toString() + "_" + LocalDateTime.now() + "." + extension;
        File settingFile = new File(thumbDirectory, savedFileName);
        thumbnail.transferTo(settingFile);
        return savedFileName;
    }

    @Transactional
    public void modifyType(ItemTypeDto itemTypeDto) {
        List<ItemCategory> itemCategory = adminItemRepository.findTypesByCategoryName(itemTypeDto.getCategoryName());
        itemCategory.forEach(ic -> {
            ic.getItem().changeItemType(itemTypeDto.getOriginType(), itemTypeDto.getType());
        });
    }

    @Transactional
    public void deleteType(ItemTypeDto itemTypeDto) {
        List<ItemCategory> itemCategories = adminItemRepository.findTypesByCategoryName(itemTypeDto.getCategoryName());
        List<Item> items = adminItemRepository.findAll();

        // Item Table ???????????? ????????? ( ???????????? ??????????????? ???????????? ???????????? ?????? ????????? ????????? ????????? ???????????? ?????? )
        items.removeIf(it -> {
            String category = itemTypeDto.getCategoryName();
            if (it instanceof CatFood && category.equals("CatFood")) {
                CatFood catFood = (CatFood) it;
                if (catFood.getType().equals(itemTypeDto.getType())) {
                    adminItemRepository.delete(catFood);
                }
            } else if (it instanceof CatToilet && category.equals("CatToilet")) {
                CatToilet catToilet = (CatToilet) it;
                if (catToilet.getType().equals(itemTypeDto.getType())) {
                    adminItemRepository.delete(catToilet);
                }
            } else if (it instanceof CatTower && category.equals("CatTower")) {
                CatTower catTower = (CatTower) it;
                if (catTower.getType().equals(itemTypeDto.getType())) {
                    adminItemRepository.delete(catTower);
                }
            }
            return false;
        });

        // ItemCategory Table ???????????? ????????? ( ????????????, ??????????????? ???????????? ??????. ??????????????? ophanremoval = true?????? )
        itemCategories.forEach(ic -> {
//             Category??? ????????? ??????????????????????????? ?????? ???????????????????????? ??????
            ic.getCategory().getItemCategory().removeIf(cic -> {
                if (cic == ic) {
                    return true;
                }
                return false;
            });
        });

    }

    @Transactional
    public void modifyItem(Item findItem, ModifyItemDto modifyItemDto, BindingResult result) {
        modifyItemDto.setId(findItem.getId());

        // ????????? ?????? ????????? ??????????????? (?????? ??????????????? ?????? ????????? ?????? ?????? ??????)
        if (modifyItemDto.getFile().getSize() == 0) {
            modifyItemDto.setThumbnailName(findItem.getThumbnailName());
            modifyItemDto.setThumbnailOriginal(findItem.getThumbnailOriginal());
        } else {
            try {
                modifyItemDto.setThumbnailName(saveThumbnail(modifyItemDto.getFile()));
                modifyItemDto.setThumbnailOriginal(modifyItemDto.getFile().getOriginalFilename());
            } catch (IOException e) {
                result.rejectValue("file", null, "????????? ????????? ????????? ???????????????.");
            }
        }
        // ???????????? ??? ??????
        if (modifyItemDto.isEvent()) {
            int price = findItem.getPrice();
            int eventPrice = (int) Math.round(price * (1 - modifyItemDto.getDiscount() / 100.0));
            findItem.setEventPrice(eventPrice);
        }
        modelMapper.map(modifyItemDto, findItem);
        if (findItem.getItemStatus() == ItemStatus.SOLDOUT && findItem.getQuantity() > 0) {
            findItem.setItemStatus(ItemStatus.NOWSALE);
            alertUtils.makeInStockAlert(findItem);
        }
    }

    @Transactional
    public void deleteItem(Long itemId) {
        adminItemRepository.deleteById(itemId);
    }

    @Transactional
    public void changeStatus(Long itemId, RedirectAttributes redirectAttributes) {
        Item findItem = adminItemRepository.findById(itemId).orElseThrow();
        ItemStatus status = findItem.getItemStatus();
        if (status == ItemStatus.READY && findItem.getQuantity() > 0) {
            findItem.setItemStatus(ItemStatus.NOWSALE);
        } else if (status == ItemStatus.NOWSALE || status == ItemStatus.SOLDOUT) {
            findItem.setItemStatus(ItemStatus.READY);
        } else {
            redirectAttributes.addFlashAttribute("statusError", "??????????????? ??????????????????");
        }
    }
}
