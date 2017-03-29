package com.movision.facade.user;

import com.movision.common.constant.MsgCodeConstant;
import com.movision.exception.BusinessException;
import com.movision.mybatis.bossMenu.entity.AuthMenu;
import com.movision.mybatis.bossMenu.entity.Menu;
import com.movision.mybatis.bossMenu.entity.MenuDetail;
import com.movision.mybatis.bossMenu.entity.MenuVo;
import com.movision.mybatis.bossMenu.service.MenuService;
import com.movision.utils.propertiesLoader.MsgPropertiesLoader;
import com.movision.utils.pagination.model.Paging;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author zhuangyuhao
 * @Date 2017/1/19 17:05
 */
@Service
public class MenuFacade {

    private static Logger log = LoggerFactory.getLogger(MenuService.class);

    @Autowired
    private MenuService menuService;

    public Boolean addMenu(Menu menu) {
        this.validateMenuNameIsExist(menu);
        return menuService.addAdminMenu(menu);
    }

    /**
     * 校验菜单名称是否已经存在
     *
     * @param menu
     */
    private void validateMenuNameIsExist(Menu menu) {
        //检验菜单名是否已经存在
        int isExist = menuService.isExistSameName(menu);
        if (isExist >= 1) {
            throw new BusinessException(MsgCodeConstant.boss_menu_name_is_exist, MsgPropertiesLoader.getValue(String.valueOf(MsgCodeConstant.boss_menu_name_is_exist)));
        }
    }

    public Boolean updateMenu(Menu menu) {
        this.validateMenuNameIsExist(menu);
        return menuService.updateMenu(menu);
    }

    public Boolean delMenu(Integer id) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setIsdel(0);
        return menuService.updateMenu(menu);
    }

    public MenuDetail queryMenuDetail(int id) {
        Menu menu = menuService.queryMenu(id);
        int pid = menu.getPid();
        String pname = null;
        if (pid != 0) {
            //查询父菜单
            Menu pmenu = menuService.queryMenu(pid);
            pname = pmenu.getMenuname();
        }
        MenuDetail menuDetail = new MenuDetail(
                menu.getId(), menu.getMenuname(), menu.getPid(), menu.getOrderid(), menu.getIsdel(), menu.getRemark(), menu.getUrl(), pname
        );
        return menuDetail;
    }



    public List<Menu> queryMenuList(Paging<Menu> pager, String menuname) {

        Map<String, Object> map = new HashedMap();
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(menuname)) {
            map.put("menuname", menuname);
        }
        return menuService.queryMenuList(pager, map);
    }


    /**
     * 获取所有菜单
     *
     * @return
     */
    public List<Map<String, Object>> getAllMenu() {
        List<AuthMenu> allParentMenuList = menuService.getAllParentMenu();
        List<AuthMenu> allChildrenMenuList = menuService.getAllChildrenMenu();

        List<Map<String, Object>> result = new ArrayList<>();

        this.getAllMenuCoreAlgorithm(allParentMenuList, allChildrenMenuList, result);
        return result;
    }

    /**
     * 组装父菜单和子菜单
     *
     * @param allParentMenuList   父菜单集合
     * @param allChildrenMenuList 子菜单集合
     * @param result              返回值list
     */
    private void getAllMenuCoreAlgorithm(List<AuthMenu> allParentMenuList, List<AuthMenu> allChildrenMenuList, List<Map<String, Object>> result) {
        for (AuthMenu pmenu : allParentMenuList) {

            //父菜单的id
            int parid = pmenu.getId();

            List<AuthMenu> sameParentMenus = new ArrayList<>();
            for (AuthMenu cmenu : allChildrenMenuList) {
                //子菜单的pid
                int pid = cmenu.getPid();
                if (pid == parid) {
                    //若是属于该父菜单的字菜单，则放入同一个list
                    sameParentMenus.add(cmenu);
                }
            }
            Map map = new HashedMap();
            map.put("parent_menu", pmenu);
            map.put("child_menu", sameParentMenus);
            result.add(map);
        }
    }

    /**
     * 获取当前角色的所有授权菜单
     *
     * @param roleid
     * @return
     */
    public List<Map<String, Object>> getAuthroizeMenu(Integer roleid) {
        List<AuthMenu> allParentMenuList = menuService.getAllParentMenu();
        List<AuthMenu> allChildrenMenuList = menuService.getAllChildrenMenu();

        List<AuthMenu> authroizeAllParentMenuList = menuService.selectAuthroizeParentMenu(roleid);
        List<AuthMenu> authroizeAllChildrenMenuList = menuService.selectAuthroizeChildrenMenu(roleid);

        //匹配操作，如果是授权的菜单，则在菜单属性中添加一个字段isAuthroized
        this.doAuthroizeOnMenu(allParentMenuList, authroizeAllParentMenuList);
        this.doAuthroizeOnMenu(allChildrenMenuList, authroizeAllChildrenMenuList);

        List<Map<String, Object>> result = new ArrayList<>();

        this.getAllMenuCoreAlgorithm(allParentMenuList, allChildrenMenuList, result);

        return result;
    }


    /**
     * 对菜单进行鉴权
     *
     * @param allParentMenuList
     * @param authroizeAllParentMenuList
     */
    private void doAuthroizeOnMenu(List<AuthMenu> allParentMenuList, List<AuthMenu> authroizeAllParentMenuList) {
        for (AuthMenu pmenu : allParentMenuList) {
            int pmenuId = pmenu.getId();
            for (AuthMenu authroizeParentMenu : authroizeAllParentMenuList) {
                int apid = authroizeParentMenu.getId();
                if (pmenuId == apid) {
                    pmenu.setAuthroize(true);
                }
            }
        }
    }

    /**
     * 查询首页侧边栏
     *
     * @return
     */
    public List<MenuVo> querySidebar(Integer roleid) {
        List<MenuVo> father = menuService.querySidebarFather(roleid);
        List<MenuVo> son = menuService.querySidebarSon(roleid);
        for (int i = 0; i < father.size(); i++) {
            List<MenuVo> mu = new ArrayList<>();
            for (int j = 0; j < son.size(); j++) {
                int z = son.get(j).getPid();
                int f = father.get(i).getId();
                if (z == f) {
                    mu.add(son.get(j));
                }
            }
            father.get(i).setSun(mu);
        }
        return father;
    }

}
