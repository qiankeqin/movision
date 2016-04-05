package com.zhuhuibao.business.memCenter.AccountManage;

import com.zhuhuibao.common.JsonResult;
import com.zhuhuibao.mybatis.memCenter.entity.Member;
import com.zhuhuibao.mybatis.memCenter.entity.WorkType;
import com.zhuhuibao.mybatis.memCenter.mapper.CertificateMapper;
import com.zhuhuibao.mybatis.memCenter.mapper.MemberMapper;
import com.zhuhuibao.mybatis.memCenter.service.MemberService;
import com.zhuhuibao.security.EncodeUtil;
import com.zhuhuibao.utils.JsonUtils;
import org.apache.shiro.crypto.hash.Md5Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员中心员工管理
 * @author cxx
 * @since 16/2/25.
 */
@RestController
public class StaffManageController {
	private static final Logger log = LoggerFactory
			.getLogger(StaffManageController.class);

	@Autowired
	private MemberService memberService;

	@Autowired
	private MemberMapper memberMapper;

	/**
	 * 新建会员
	 * @param req
	 * @return
	 * @throws IOException
	 */

	@RequestMapping(value = "/rest/addMember", method = RequestMethod.POST)
	public void addMember(HttpServletRequest req, HttpServletResponse response, Member member) throws IOException {
		String account = req.getParameter("account");
		if(account.contains("@")){
			member.setEmail(account);
		}else{
			member.setMobile(account);
		}
		JsonResult result = new JsonResult();

		String md5Pwd = new Md5Hash("123456",null,2).toString();
		member.setPassword(md5Pwd);
		//先判断账号是否已经存在
		Member mem = memberService.findMem(member);
		if(mem==null){
			int isAdd = memberService.addMember(member);
			if(isAdd==0){
				result.setCode(400);
				result.setMessage("新增失败");
			}else{
				result.setCode(200);
			}
		}else{
			result.setCode(400);
			result.setMessage("账号已经存在");
		}

		response.setContentType("application/json;charset=utf-8");
		response.getWriter().write(JsonUtils.getJsonStringFromObj(result));
	}

	/**
	 * 修改会员
	 * @param req
	 * @return
	 * @throws IOException
	 */

	@RequestMapping(value = "/rest/updateMember", method = RequestMethod.POST)
	public void updateMember(HttpServletRequest req, HttpServletResponse response, Member member) throws IOException {
		String account = req.getParameter("account");
		if(account.contains("@")){
			member.setEmail(account);
		}else{
			member.setMobile(account);
		}

		JsonResult result = new JsonResult();

		int isUpdate = memberService.updateMember(member);
		if(isUpdate==0){
			result.setCode(400);
			result.setMessage("修改失败");
		}else{
			result.setCode(200);
		}

		response.setContentType("application/json;charset=utf-8");
		response.getWriter().write(JsonUtils.getJsonStringFromObj(result));
	}

	/**
	 * 禁用会员
	 * @param req
	 * @return
	 * @throws IOException
	 */

/*	@RequestMapping(value = "/rest/disableMember", method = RequestMethod.POST)
	public void disableMember(HttpServletRequest req, HttpServletResponse response,Member member) throws IOException {
		JsonResult result = new JsonResult();
		int isDisable = memberService.disableMember(member);
		if(isDisable==0){
			result.setCode(400);
			result.setMessage("禁用失败");
		}else{
			result.setCode(200);
		}
		response.setContentType("application/json;charset=utf-8");
		response.getWriter().write(JsonUtils.getJsonStringFromObj(result));
	}*/

	/**
	 * 删除会员
	 * @param req
	 * @return
	 * @throws IOException
	 */

	@RequestMapping(value = "/rest/deleteMember", method = RequestMethod.POST)
	public void deleteMember(HttpServletRequest req, HttpServletResponse response) throws IOException {
		String ids[] = req.getParameterValues("ids");
		JsonResult result = new JsonResult();
		for(int i=0;i<ids.length;i++){
			String id = ids[i];
			int isdelete = memberService.deleteMember(id);
			if(isdelete==0){
				result.setCode(400);
				result.setMessage("删除失败");
			}else{
				result.setCode(200);
			}
		}
		response.setContentType("application/json;charset=utf-8");
		response.getWriter().write(JsonUtils.getJsonStringFromObj(result));
	}

	/**
	 * 员工搜索
	 * @param req
	 * @return
	 * @throws IOException
	 */

	@RequestMapping(value = "/rest/staffSearch", method = RequestMethod.GET)
	public void staffSearch(HttpServletRequest req, HttpServletResponse response,Member member) throws IOException {
		if(member.getAccount()!=null){
			if(member.getAccount().contains("_")){
				member.setAccount(member.getAccount().replace("_","\\_"));
			}
		}
		JsonResult result = memberService.findStaffByParentId(member);
		response.setContentType("application/json;charset=utf-8");
		response.getWriter().write(JsonUtils.getJsonStringFromObj(result));
	}

	/**
	 * 员工搜索size
	 * @param req
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/rest/staffSearchSize", method = RequestMethod.GET)
	public void staffSearchSize(HttpServletRequest req, HttpServletResponse response,Member member) throws IOException {
		JsonResult result = new JsonResult();
		List<Member> memberList = memberMapper.findStaffByParentId(member);
		Map map = new HashMap();
		map.put("size",memberList.size());
		map.put("leftSize",30-memberList.size());
		result.setCode(200);
		result.setData(map);
		response.setContentType("application/json;charset=utf-8");
		response.getWriter().write(JsonUtils.getJsonStringFromObj(result));
	}

	/**
	 * 员工密码重置
	 * @param req
	 * @return
	 * @throws IOException
	 */

	@RequestMapping(value = "/rest/resetPwd", method = RequestMethod.POST)
	public void resetPwd(HttpServletRequest req, HttpServletResponse response) throws IOException {
		String ids[] = req.getParameterValues("ids");
		JsonResult result = new JsonResult();
		for(int i=0;i<ids.length;i++){
			String id = ids[i];
			Member member = new Member();
			String md5Pwd = new Md5Hash("123456",null,2).toString();
			member.setPassword(md5Pwd);
			int isReset = memberService.resetPwd(id);
			if(isReset==0){
				result.setCode(400);
				result.setMessage("重置失败");
			}else{
				result.setCode(200);
			}
		}
		response.setContentType("application/json;charset=utf-8");
		response.getWriter().write(JsonUtils.getJsonStringFromObj(result));
	}
}
