package cn.com.mfish.oauth.controller;

import cn.com.mfish.common.core.enums.DeviceType;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import cn.com.mfish.common.core.utils.StringUtils;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.oauth.api.entity.UserInfo;
import cn.com.mfish.common.oauth.api.entity.UserRole;
import cn.com.mfish.common.oauth.api.vo.UserInfoVo;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.oauth.common.OauthUtils;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.oauth.cache.redis.RedisSessionDAO;
import cn.com.mfish.oauth.cache.redis.UserTokenCache;
import cn.com.mfish.oauth.entity.OnlineUser;
import cn.com.mfish.oauth.entity.SsoUser;
import cn.com.mfish.oauth.req.ReqChangePwd;
import cn.com.mfish.oauth.req.ReqSsoUser;
import cn.com.mfish.oauth.service.OAuth2Service;
import cn.com.mfish.oauth.service.SsoUserService;
import com.github.pagehelper.PageHelper;
import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

/**
 * @author: mfish
 * @date: 2020/2/17 18:49
 */
@Api(tags = "用户信息")
@RestController
@RequestMapping("/user")
@Slf4j
public class SsoUserController {

    @Resource
    OAuth2Service oAuth2Service;
    @Resource
    UserTokenCache userTokenCache;
    @Resource
    SsoUserService ssoUserService;
    @Resource
    RedisSessionDAO redisSessionDAO;

    @ApiOperation("获取用户、权限相关信息")
    @GetMapping("/info")
    @Log(title = "获取用户、权限相关信息", operateType = OperateType.QUERY)
    public Result<UserInfoVo> getUserInfo() {
        return Result.ok(oAuth2Service.getUserInfoAndRoles(AuthInfoUtils.getCurrentUserId(), AuthInfoUtils.getCurrentClientId()));
    }

    @ApiOperation("获取用户权限")
    @GetMapping("/permissions")
    @Log(title = "获取用户权限", operateType = OperateType.QUERY)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "userId", value = "用户ID"),
            @ApiImplicitParam(name = "clientId", value = "客户端ID")
    })
    public Result<Set<String>> getPermissions(String userId, String clientId) {
        if (StringUtils.isEmpty(userId)) {
            userId = AuthInfoUtils.getCurrentUserId();
        }
        if (StringUtils.isEmpty(clientId)) {
            clientId = AuthInfoUtils.getCurrentClientId();
        }
        return Result.ok(ssoUserService.getUserPermissions(userId, clientId));
    }

    @ApiOperation("获取用户角色")
    @GetMapping("/roles")
    @Log(title = "获取用户权限", operateType = OperateType.QUERY)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "userId", value = "用户ID"),
            @ApiImplicitParam(name = "clientId", value = "客户端ID")
    })
    public Result<List<UserRole>> getRoles(String userId, String clientId) {
        if (StringUtils.isEmpty(userId)) {
            userId = AuthInfoUtils.getCurrentUserId();
        }
        if (StringUtils.isEmpty(clientId)) {
            clientId = AuthInfoUtils.getCurrentClientId();
        }
        return Result.ok(ssoUserService.getUserRoles(userId, clientId));
    }

    @ApiOperation("通过用户ID获取用户")
    @GetMapping("/{id}")
    public Result<UserInfo> getUserById(@ApiParam(name = "id", value = "用户ID") @PathVariable String id) {
        return Result.ok(ssoUserService.getUserByIdNoPwd(id));
    }

    @ApiOperation("修改密码")
    @PutMapping("/pwd")
    @Log(title = "修改密码", operateType = OperateType.UPDATE)
    public Result<Boolean> changePassword(@RequestBody ReqChangePwd reqChangePwd) {
        if (StringUtils.isEmpty(reqChangePwd.getUserId())) {
            Result.fail(false, "错误:用户ID不允许为空");
        }
        //除了超户，其他用户修改密码需要传入旧密码
        //超户修改自己密码需要输入旧密码
        if (StringUtils.isEmpty(reqChangePwd.getOldPwd()) && (!AuthInfoUtils.isSuper() || AuthInfoUtils.isSuper(reqChangePwd.getUserId()))) {
            return Result.fail(true, "错误:未输入旧密码");
        }
        return ssoUserService.changePassword(reqChangePwd.getUserId(), reqChangePwd.getOldPwd(), reqChangePwd.getNewPwd());
    }

    @Log(title = "用户-设置状态", operateType = OperateType.UPDATE)
    @ApiOperation(value = "用户-设置状态", notes = "用户-设置状态")
    @PutMapping("/status")
    @RequiresPermissions("sys:account:update")
    public Result<Boolean> setStatus(@RequestBody SsoUser ssoUser) {
        SsoUser newUser = new SsoUser();
        newUser.setStatus(ssoUser.getStatus()).setId(ssoUser.getId());
        Result<SsoUser> result = ssoUserService.updateUser(newUser);
        if (result.isSuccess()) {
            return Result.ok(true, "用户-设置状态成功!");
        }
        return Result.fail(false, "错误:用户-设置状态失败!");
    }

    @ApiOperation("用户登出")
    @GetMapping("/revoke")
    @Log(title = "用户登出", operateType = OperateType.LOGOUT)
    public Result<String> revoke() {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null) {
            String error = "未获取到用户登录状态,无需登出";
            return Result.ok(error);
        }
        String userId = (String) subject.getPrincipal();
        userTokenCache.delUserDevice(DeviceType.Web, userId);
        subject.logout();
        return Result.ok("成功登出");
    }

    /**
     * 分页列表查询
     *
     * @param reqSsoUser
     * @param reqPage
     * @return
     */
    @ApiOperation(value = "用户信息-分页列表查询", notes = "用户信息-分页列表查询")
    @GetMapping
    @RequiresPermissions("sys:account:query")
    public Result<PageResult<UserInfo>> queryPageList(ReqSsoUser reqSsoUser, ReqPage reqPage) {
        PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        List<UserInfo> pageList = ssoUserService.getUserList(reqSsoUser);
        return Result.ok(new PageResult<>(pageList), "用户信息-查询成功!");
    }

    @Log(title = "用户信息-添加", operateType = OperateType.INSERT)
    @ApiOperation(value = "用户信息-添加", notes = "用户信息-添加")
    @PostMapping
    @RequiresPermissions("sys:account:insert")
    public Result<SsoUser> add(@RequestBody SsoUser ssoUser) {
        return ssoUserService.insertUser(ssoUser);
    }

    /**
     * 编辑
     *
     * @param ssoUser
     * @return
     */
    @Log(title = "用户信息-编辑", operateType = OperateType.UPDATE)
    @ApiOperation(value = "用户信息-编辑", notes = "用户信息-编辑")
    @PutMapping
    @RequiresPermissions("sys:account:update")
    public Result<SsoUser> edit(@RequestBody SsoUser ssoUser) {
        return ssoUserService.updateUser(ssoUser);
    }

    /**
     * 通过id删除
     *
     * @param id
     * @return
     */
    @Log(title = "用户信息-通过id删除", operateType = OperateType.DELETE)
    @ApiOperation(value = "用户信息-通过id删除", notes = "用户信息-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("sys:account:delete")
    public Result<Boolean> delete(@ApiParam(name = "id", value = "唯一性ID") @PathVariable String id) {
        if ("1".equals(id)) {
            return Result.fail(false, "错误:admin帐号不允许删除!");
        }
        if (ssoUserService.removeUser(id)) {
            return Result.ok(true, "用户信息-删除成功!");
        }
        return Result.fail(false, "错误:用户信息-删除失败!");
    }

    @ApiOperation("判断用户是否存在")
    @GetMapping("/exist/{account}")
    public Result<Boolean> isAccountExist(@ApiParam(name = "account", value = "帐号名称") @PathVariable String account) {
        if (ssoUserService.isAccountExist(account, null)) {
            return Result.fail(true, "帐号[" + account + "]存在");
        }
        return Result.ok(false, "帐号[" + account + "]不存在");
    }

    @ApiOperation("获取在线用户信息")
    @GetMapping("/online")
    @RequiresPermissions("sys:online:query")
    public Result<PageResult<OnlineUser>> userOnline(ReqPage reqPage) {
        return Result.ok(oAuth2Service.getOnlineUser(reqPage), "获取在线用户成功");
    }

    @ApiOperation("踢出指定用户")
    @GetMapping("/revoke/{token}")
    @Log(title = "踢出指定用户", operateType = OperateType.LOGOUT)
    @RequiresPermissions("sys:online:revoke")
    public Result<String> revokeUser(@ApiParam(name = "token", value = "指定用户的token") @PathVariable String token) {
        String sessionId = OauthUtils.logout(oAuth2Service.decryptToken(token));
        try {
            redisSessionDAO.delete(redisSessionDAO.readSession(sessionId));
        } catch (Exception ex) {
            log.error("删除session异常", ex);
        }
        return Result.ok("成功登出");
    }
}
