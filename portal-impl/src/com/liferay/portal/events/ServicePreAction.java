/**
 * Copyright (c) 2000-2008 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portal.events;

import com.liferay.portal.LayoutPermissionException;
import com.liferay.portal.NoSuchGroupException;
import com.liferay.portal.NoSuchLayoutException;
import com.liferay.portal.PortalException;
import com.liferay.portal.SystemException;
import com.liferay.portal.kernel.events.Action;
import com.liferay.portal.kernel.events.ActionException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.lar.PortletDataHandlerKeys;
import com.liferay.portal.kernel.portlet.LiferayWindowState;
import com.liferay.portal.kernel.servlet.BrowserSnifferUtil;
import com.liferay.portal.kernel.servlet.ImageServletTokenUtil;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PropertiesUtil;
import com.liferay.portal.kernel.util.SafeProperties;
import com.liferay.portal.kernel.util.StringMaker;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.ColorScheme;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Image;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutConstants;
import com.liferay.portal.model.LayoutSet;
import com.liferay.portal.model.LayoutTypePortlet;
import com.liferay.portal.model.Theme;
import com.liferay.portal.model.User;
import com.liferay.portal.model.impl.ColorSchemeImpl;
import com.liferay.portal.model.impl.GroupImpl;
import com.liferay.portal.model.impl.LayoutImpl;
import com.liferay.portal.model.impl.LayoutTypePortletImpl;
import com.liferay.portal.model.impl.ThemeImpl;
import com.liferay.portal.security.permission.ActionKeys;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.security.permission.PermissionCheckerFactory;
import com.liferay.portal.security.permission.PermissionCheckerImpl;
import com.liferay.portal.security.permission.PermissionThreadLocal;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.ImageLocalServiceUtil;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.service.LayoutSetLocalServiceUtil;
import com.liferay.portal.service.OrganizationLocalServiceUtil;
import com.liferay.portal.service.ThemeLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.service.permission.GroupPermissionUtil;
import com.liferay.portal.service.permission.LayoutPermissionUtil;
import com.liferay.portal.service.permission.OrganizationPermissionUtil;
import com.liferay.portal.service.permission.UserPermissionUtil;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.theme.ThemeDisplayFactory;
import com.liferay.portal.util.CookieKeys;
import com.liferay.portal.util.LayoutClone;
import com.liferay.portal.util.LayoutCloneFactory;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.PortletKeys;
import com.liferay.portal.util.PropsUtil;
import com.liferay.portal.util.PropsValues;
import com.liferay.portal.util.WebKeys;
import com.liferay.portlet.PortletURLImpl;
import com.liferay.util.ListUtil;
import com.liferay.util.Normalizer;
import com.liferay.util.dao.hibernate.QueryUtil;

import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import javax.portlet.PortletMode;
import javax.portlet.PortletRequest;
import javax.portlet.PortletURL;
import javax.portlet.WindowState;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.time.StopWatch;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.Globals;

/**
 * <a href="ServicePreAction.java.html"><b><i>View Source</i></b></a>
 *
 * @author Brian Wing Shun Chan
 * @author Felix Ventero
 *
 */
public class ServicePreAction extends Action {

	public ServicePreAction() {
		initImportLARFiles();
	}

	public void run(HttpServletRequest req, HttpServletResponse res)
		throws ActionException {

		StopWatch stopWatch = null;

		if (_log.isDebugEnabled()) {
			stopWatch = new StopWatch();

			stopWatch.start();
		}

		try {
			servicePre(req, res);
		}
		catch (Exception e) {
			throw new ActionException(e);
		}

		if (_log.isDebugEnabled()) {
			_log.debug("Running takes " + stopWatch.getTime() + " ms");
		}
	}

	protected void addDefaultLayoutsByLAR(
			long userId, long groupId, boolean privateLayout, File larFile)
		throws PortalException, SystemException {

		Map<String, String[]> parameterMap = new HashMap<String, String[]>();

		parameterMap.put(
			PortletDataHandlerKeys.PERMISSIONS,
			new String[] {Boolean.TRUE.toString()});
		parameterMap.put(
			PortletDataHandlerKeys.USER_PERMISSIONS,
			new String[] {Boolean.FALSE.toString()});
		parameterMap.put(
			PortletDataHandlerKeys.PORTLET_DATA,
			new String[] {Boolean.TRUE.toString()});
		parameterMap.put(
			PortletDataHandlerKeys.PORTLET_DATA_CONTROL_DEFAULT,
			new String[] {Boolean.TRUE.toString()});
		parameterMap.put(
			PortletDataHandlerKeys.PORTLET_SETUP,
			new String[] {Boolean.TRUE.toString()});

		LayoutLocalServiceUtil.importLayouts(
			userId, groupId, privateLayout, parameterMap, larFile);
	}

	protected void addDefaultUserPrivateLayoutByProperties(
			long userId, long groupId)
		throws PortalException, SystemException {

		String friendlyURL = getFriendlyURL(
			PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_FRIENDLY_URL);

		Layout layout = LayoutLocalServiceUtil.addLayout(
			userId, groupId, true, LayoutConstants.DEFAULT_PARENT_LAYOUT_ID,
			PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_NAME, StringPool.BLANK,
			StringPool.BLANK, LayoutConstants.TYPE_PORTLET, false, friendlyURL);

		LayoutTypePortlet layoutTypePortlet =
			(LayoutTypePortlet)layout.getLayoutType();

		layoutTypePortlet.setLayoutTemplateId(
			0, PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_TEMPLATE_ID, false);

		for (int i = 0; i < 10; i++) {
			String columnId = "column-" + i;
			String portletIds = PropsUtil.get(
				PropsUtil.DEFAULT_USER_PRIVATE_LAYOUT_COLUMN + i);

			String[] portletIdsArray = StringUtil.split(portletIds);

			layoutTypePortlet.addPortletIds(
				0, portletIdsArray, columnId, false);
		}

		LayoutLocalServiceUtil.updateLayout(
			layout.getGroupId(), layout.isPrivateLayout(), layout.getLayoutId(),
			layout.getTypeSettings());

		boolean updateLayoutSet = false;

		LayoutSet layoutSet = layout.getLayoutSet();

		if (Validator.isNotNull(
				PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_REGULAR_THEME_ID)) {

			layoutSet.setThemeId(
				PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_REGULAR_THEME_ID);

			updateLayoutSet = true;
		}

		if (Validator.isNotNull(
				PropsValues.
					DEFAULT_USER_PRIVATE_LAYOUT_REGULAR_COLOR_SCHEME_ID)) {

			layoutSet.setColorSchemeId(
				PropsValues.
					DEFAULT_USER_PRIVATE_LAYOUT_REGULAR_COLOR_SCHEME_ID);

			updateLayoutSet = true;
		}

		if (Validator.isNotNull(
				PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_WAP_THEME_ID)) {

			layoutSet.setWapThemeId(
				PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_WAP_THEME_ID);

			updateLayoutSet = true;
		}

		if (Validator.isNotNull(
				PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_WAP_COLOR_SCHEME_ID)) {

			layoutSet.setWapColorSchemeId(
				PropsValues.DEFAULT_USER_PRIVATE_LAYOUT_WAP_COLOR_SCHEME_ID);

			updateLayoutSet = true;
		}

		if (updateLayoutSet) {
			LayoutSetLocalServiceUtil.updateLayoutSet(layoutSet);
		}
	}

	protected void addDefaultUserPrivateLayouts(User user)
		throws PortalException, SystemException {

		if (!PropsValues.LAYOUT_USER_PRIVATE_LAYOUTS_AUTO_CREATE) {
			return;
		}

		if (!user.hasPrivateLayouts()) {
			Group userGroup = user.getGroup();

			if (privateLARFile != null) {
				addDefaultLayoutsByLAR(
					user.getUserId(), userGroup.getGroupId(), true,
					privateLARFile);
			}
			else {
				addDefaultUserPrivateLayoutByProperties(
					user.getUserId(), userGroup.getGroupId());
			}
		}
	}

	protected void addDefaultUserPublicLayoutByProperties(
			long userId, long groupId)
		throws PortalException, SystemException {

		String friendlyURL = getFriendlyURL(
			PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_FRIENDLY_URL);

		Layout layout = LayoutLocalServiceUtil.addLayout(
			userId, groupId, false, LayoutConstants.DEFAULT_PARENT_LAYOUT_ID,
			PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_NAME, StringPool.BLANK,
			StringPool.BLANK, LayoutConstants.TYPE_PORTLET, false, friendlyURL);

		LayoutTypePortlet layoutTypePortlet =
			(LayoutTypePortlet)layout.getLayoutType();

		layoutTypePortlet.setLayoutTemplateId(
			0, PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_TEMPLATE_ID, false);

		for (int i = 0; i < 10; i++) {
			String columnId = "column-" + i;
			String portletIds = PropsUtil.get(
				PropsUtil.DEFAULT_USER_PUBLIC_LAYOUT_COLUMN + i);

			String[] portletIdsArray = StringUtil.split(portletIds);

			layoutTypePortlet.addPortletIds(
				0, portletIdsArray, columnId, false);
		}

		LayoutLocalServiceUtil.updateLayout(
			layout.getGroupId(), layout.isPrivateLayout(), layout.getLayoutId(),
			layout.getTypeSettings());

		boolean updateLayoutSet = false;

		LayoutSet layoutSet = layout.getLayoutSet();

		if (Validator.isNotNull(
				PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_REGULAR_THEME_ID)) {

			layoutSet.setThemeId(
				PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_REGULAR_THEME_ID);

			updateLayoutSet = true;
		}

		if (Validator.isNotNull(
				PropsValues.
					DEFAULT_USER_PUBLIC_LAYOUT_REGULAR_COLOR_SCHEME_ID)) {

			layoutSet.setColorSchemeId(
				PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_REGULAR_COLOR_SCHEME_ID);

			updateLayoutSet = true;
		}

		if (Validator.isNotNull(
				PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_WAP_THEME_ID)) {

			layoutSet.setWapThemeId(
				PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_WAP_THEME_ID);

			updateLayoutSet = true;
		}

		if (Validator.isNotNull(
				PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_WAP_COLOR_SCHEME_ID)) {

			layoutSet.setWapColorSchemeId(
				PropsValues.DEFAULT_USER_PUBLIC_LAYOUT_WAP_COLOR_SCHEME_ID);

			updateLayoutSet = true;
		}

		if (updateLayoutSet) {
			LayoutSetLocalServiceUtil.updateLayoutSet(layoutSet);
		}
	}

	protected void addDefaultUserPublicLayouts(User user)
		throws PortalException, SystemException {

		if (!PropsValues.LAYOUT_USER_PUBLIC_LAYOUTS_AUTO_CREATE) {
			return;
		}

		if (!user.hasPublicLayouts()) {
			Group userGroup = user.getGroup();

			if (publicLARFile != null) {
				addDefaultLayoutsByLAR(
					user.getUserId(), userGroup.getGroupId(), true,
					publicLARFile);
			}
			else {
				addDefaultUserPublicLayoutByProperties(
					user.getUserId(), userGroup.getGroupId());
			}
		}
	}

	protected void deleteDefaultUserPrivateLayouts(User user)
		throws PortalException, SystemException {

		if (!PropsValues.LAYOUT_USER_PRIVATE_LAYOUTS_ENABLED &&
			user.hasPrivateLayouts()) {

			Group userGroup = user.getGroup();

			LayoutLocalServiceUtil.deleteLayouts(userGroup.getGroupId(), true);
		}
	}

	protected void deleteDefaultUserPublicLayouts(User user)
		throws PortalException, SystemException {

		if (!PropsValues.LAYOUT_USER_PUBLIC_LAYOUTS_ENABLED &&
			user.hasPublicLayouts()) {

			Group userGroup = user.getGroup();

			LayoutLocalServiceUtil.deleteLayouts(userGroup.getGroupId(), false);
		}
	}

	protected Object[] getDefaultLayout(
			HttpServletRequest req, User user, boolean signedIn)
		throws PortalException, SystemException {

		// Check the virtual host

		LayoutSet layoutSet = (LayoutSet)req.getAttribute(
			WebKeys.VIRTUAL_HOST_LAYOUT_SET);

		if (layoutSet != null) {
			List<Layout> layouts = LayoutLocalServiceUtil.getLayouts(
				layoutSet.getGroupId(), layoutSet.isPrivateLayout(),
				LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);

			if (layouts.size() > 0) {
				Layout layout = layouts.get(0);

				return new Object[] {layout, layouts};
			}
		}

		Layout layout = null;
		List<Layout> layouts = null;

		if (signedIn) {

			// Check the user's personal layouts

			Group userGroup = user.getGroup();

			layouts = LayoutLocalServiceUtil.getLayouts(
				userGroup.getGroupId(), true,
				LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);

			if (layouts.size() == 0) {
				layouts = LayoutLocalServiceUtil.getLayouts(
					userGroup.getGroupId(), false,
					LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);
			}

			if (layouts.size() > 0) {
				layout = layouts.get(0);
			}

			// Check the user's communities

			if (layout == null) {
				LinkedHashMap<String, Object> groupParams =
					new LinkedHashMap<String, Object>();

				groupParams.put("usersGroups", new Long(user.getUserId()));

				List<Group> groups = GroupLocalServiceUtil.search(
					user.getCompanyId(), null, null, groupParams,
					QueryUtil.ALL_POS, QueryUtil.ALL_POS);

				for (int i = 0; i < groups.size(); i++) {
					Group group = groups.get(i);

					layouts = LayoutLocalServiceUtil.getLayouts(
						group.getGroupId(), true,
						LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);

					if (layouts.size() == 0) {
						layouts = LayoutLocalServiceUtil.getLayouts(
							group.getGroupId(), false,
							LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);
					}

					if (layouts.size() > 0) {
						layout = layouts.get(0);

						break;
					}
				}
			}
		}
		else {

			// Check the guest community

			Group guestGroup = GroupLocalServiceUtil.getGroup(
				user.getCompanyId(), GroupImpl.GUEST);

			layouts = LayoutLocalServiceUtil.getLayouts(
				guestGroup.getGroupId(), false,
				LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);

			if (layouts.size() > 0) {
				layout = layouts.get(0);
			}
		}

		return new Object[] {layout, layouts};
	}

	protected String getFriendlyURL(String friendlyURL) {
		friendlyURL = GetterUtil.getString(friendlyURL);

		return Normalizer.normalizeToAscii(friendlyURL.trim().toLowerCase());
	}

	protected Object[] getViewableLayouts(
			HttpServletRequest req, User user,
			PermissionChecker permissionChecker, Layout layout,
			List<Layout> layouts)
		throws PortalException, SystemException {

		if ((layouts == null) || (layouts.size() == 0)) {
			return new Object[] {layout, layouts};
		}

		boolean replaceLayout = true;

		if (LayoutPermissionUtil.contains(
				permissionChecker, layout, ActionKeys.VIEW)) {

			replaceLayout = false;
		}

		List<Layout> accessibleLayouts = new ArrayList<Layout>();

		for (int i = 0; i < layouts.size(); i++) {
			Layout curLayout = layouts.get(i);

			if (!curLayout.isHidden() &&
				LayoutPermissionUtil.contains(
					permissionChecker, curLayout, ActionKeys.VIEW)) {

				if ((accessibleLayouts.size() == 0) && replaceLayout) {
					layout = curLayout;
				}

				accessibleLayouts.add(curLayout);
			}
		}

		if (accessibleLayouts.size() == 0) {
			layouts = null;

			SessionErrors.add(req, LayoutPermissionException.class.getName());
		}
		else {
			layouts = accessibleLayouts;
		}

		return new Object[] {layout, layouts};
	}

	protected void initImportLARFiles() {
		String privateLARFileName =
			PropsValues.DEFAULT_USER_PRIVATE_LAYOUTS_LAR;

		if (_log.isDebugEnabled()) {
			_log.debug("Reading private LAR file " + privateLARFileName);
		}

		if (Validator.isNotNull(privateLARFileName)) {
			privateLARFile = new File(privateLARFileName);

			if (!privateLARFile.exists()) {
				_log.error(
					"Private LAR file " + privateLARFile + " does not exist");

				privateLARFile = null;
			}
			else {
				if (_log.isDebugEnabled()) {
					_log.debug("Using private LAR file " + privateLARFileName);
				}
			}
		}

		String publicLARFileName = PropsValues.DEFAULT_USER_PUBLIC_LAYOUTS_LAR;

		if (_log.isDebugEnabled()) {
			_log.debug("Reading public LAR file " + publicLARFileName);
		}

		if (Validator.isNotNull(publicLARFileName)) {
			publicLARFile = new File(publicLARFileName);

			if (!publicLARFile.exists()) {
				_log.error(
					"Public LAR file " + publicLARFile + " does not exist");

				publicLARFile = null;
			}
			else {
				if (_log.isDebugEnabled()) {
					_log.debug("Using public LAR file " + publicLARFileName);
				}
			}
		}
	}

	protected boolean isViewableCommunity(
			User user, long groupId, boolean privateLayout,
			PermissionChecker permissionChecker)
		throws PortalException, SystemException {

		Group group = GroupLocalServiceUtil.getGroup(groupId);

		// Inactive communities are not viewable

		if (!group.isActive()) {
			return false;
		}
		else if (group.isStagingGroup()) {
			Group liveGroup = group.getLiveGroup();

			if (!liveGroup.isActive()) {
				return false;
			}
		}

		// User private layouts are only viewable by the user and anyone who can
		// update the user. The user must also be active.

		if (group.isUser()) {
			long groupUserId = group.getClassPK();

			if (groupUserId == user.getUserId()) {
				return true;
			}
			else {
				User groupUser = UserLocalServiceUtil.getUserById(groupUserId);

				if (!groupUser.isActive()) {
					return false;
				}

				if (privateLayout) {
					if (UserPermissionUtil.contains(
							permissionChecker, groupUserId,
							groupUser.getOrganizationIds(),
							ActionKeys.UPDATE)) {

						return true;
					}
					else {
						return false;
					}
				}
			}
		}

		// Most public layouts are viewable

		if (!privateLayout) {
			return true;
		}

		// If the current group is staging, the live group should be checked
		// for membership instead

		if (group.isStagingGroup()) {
			groupId = group.getLiveGroupId();
		}

		// Community or organization layouts are only viewable by users who
		// belong to the community or organization, or by users who can update
		// the community or organization

		if (group.isCommunity()) {
			if (GroupLocalServiceUtil.hasUserGroup(user.getUserId(), groupId)) {
				return true;
			}
			else if (GroupPermissionUtil.contains(
						permissionChecker, groupId, ActionKeys.UPDATE)) {

				return true;
			}
		}
		else if (group.isOrganization()) {
			long organizationId = group.getClassPK();

			if (OrganizationLocalServiceUtil.hasUserOrganization(
					user.getUserId(), organizationId)) {

				return true;
			}
			else if (OrganizationPermissionUtil.contains(
						permissionChecker, organizationId, ActionKeys.UPDATE)) {

				return true;
			}
		}
		else if (group.isUserGroup()) {
			if (GroupPermissionUtil.contains(
					permissionChecker, groupId, ActionKeys.MANAGE_LAYOUTS)) {

				return true;
			}
		}

		return false;
	}

	protected List<Layout> mergeAdditionalLayouts(
			HttpServletRequest req, User user,
			PermissionChecker permissionChecker, Layout layout,
			List<Layout> layouts)
		throws PortalException, SystemException {

		if ((layout == null) || layout.isPrivateLayout()) {
			return layouts;
		}

		long layoutGroupId = layout.getGroupId();

		Group guestGroup = GroupLocalServiceUtil.getGroup(
			user.getCompanyId(), GroupImpl.GUEST);

		if (layoutGroupId != guestGroup.getGroupId()) {
			Group layoutGroup = GroupLocalServiceUtil.getGroup(layoutGroupId);

			Properties props = layoutGroup.getTypeSettingsProperties();

			boolean mergeGuestPublicPages = GetterUtil.getBoolean(
				props.getProperty("mergeGuestPublicPages"));

			if (!mergeGuestPublicPages) {
				return layouts;
			}

			List<Layout> guestLayouts = LayoutLocalServiceUtil.getLayouts(
				guestGroup.getGroupId(), false,
				LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);

			Object[] viewableLayouts = getViewableLayouts(
				req, user, permissionChecker, layout, guestLayouts);

			guestLayouts = (List<Layout>)viewableLayouts[1];

			layouts.addAll(0, guestLayouts);
		}
		else {
			HttpSession ses = req.getSession();

			Long previousGroupId = (Long)ses.getAttribute(
				WebKeys.LIFERAY_SHARED_VISITED_GROUP_ID_PREVIOUS);

			if ((previousGroupId != null) &&
				(previousGroupId.longValue() != layoutGroupId)) {

				Group previousGroup = null;

				try {
					previousGroup = GroupLocalServiceUtil.getGroup(
						previousGroupId.longValue());
				}
				catch (NoSuchGroupException nsge) {
					if (_log.isWarnEnabled()) {
						_log.warn(nsge);
					}

					return layouts;
				}

				Properties props = previousGroup.getTypeSettingsProperties();

				boolean mergeGuestPublicPages = GetterUtil.getBoolean(
					props.getProperty("mergeGuestPublicPages"));

				if (!mergeGuestPublicPages) {
					return layouts;
				}

				List<Layout> previousLayouts =
					LayoutLocalServiceUtil.getLayouts(
						previousGroupId.longValue(), false,
						LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);

				Object[] viewableLayouts = getViewableLayouts(
					req, user, permissionChecker, layout, previousLayouts);

				previousLayouts = (List<Layout>)viewableLayouts[1];

				layouts.addAll(previousLayouts);
			}
		}

		return layouts;
	}

	protected void rememberVisitedGroupIds(
		HttpServletRequest req, long currentGroupId) {

		String requestURI = GetterUtil.getString(req.getRequestURI());

		if (!requestURI.endsWith(_PATH_PORTAL_LAYOUT)) {
			return;
		}

		HttpSession ses = req.getSession();

		Long recentGroupId = (Long)ses.getAttribute(
			WebKeys.LIFERAY_SHARED_VISITED_GROUP_ID_RECENT);

		Long previousGroupId = (Long)ses.getAttribute(
			WebKeys.LIFERAY_SHARED_VISITED_GROUP_ID_PREVIOUS);

		if (recentGroupId == null) {
			recentGroupId = new Long(currentGroupId);

			ses.setAttribute(
				WebKeys.LIFERAY_SHARED_VISITED_GROUP_ID_RECENT,
				recentGroupId);
		}
		else if (recentGroupId.longValue() != currentGroupId) {
			previousGroupId = new Long(recentGroupId.longValue());

			recentGroupId = new Long(currentGroupId);

			ses.setAttribute(
				WebKeys.LIFERAY_SHARED_VISITED_GROUP_ID_RECENT,
				recentGroupId);

			ses.setAttribute(
				WebKeys.LIFERAY_SHARED_VISITED_GROUP_ID_PREVIOUS,
				previousGroupId);
		}

		if (_log.isDebugEnabled()) {
			_log.debug("Current group id " + currentGroupId);
			_log.debug("Recent group id " + recentGroupId);
			_log.debug("Previous group id " + previousGroupId);
		}
	}

	protected void servicePre(HttpServletRequest req, HttpServletResponse res)
		throws Exception {

		HttpSession ses = req.getSession();

		// Company

		Company company = PortalUtil.getCompany(req);

		long companyId = company.getCompanyId();

		// CDN host

		String cdnHost = ParamUtil.getString(
			req, "cdn_host", PortalUtil.getCDNHost());

		// Portal URL

		String portalURL = PortalUtil.getPortalURL(req);

		// Paths

		String contextPath = PortalUtil.getPathContext();
		String friendlyURLPrivateGroupPath =
			PortalUtil.getPathFriendlyURLPrivateGroup();
		String friendlyURLPrivateUserPath =
			PortalUtil.getPathFriendlyURLPrivateUser();
		String friendlyURLPublicPath = PortalUtil.getPathFriendlyURLPublic();
		String imagePath = PortalUtil.getPathImage();
		String mainPath = PortalUtil.getPathMain();

		// Company logo

		String companyLogo =
			imagePath + "/company_logo?img_id=" + company.getLogoId() + "&t=" +
				ImageServletTokenUtil.getToken(company.getLogoId());

		Image companyLogoImage = ImageLocalServiceUtil.getCompanyLogo(
			company.getLogoId());

		int companyLogoHeight = companyLogoImage.getHeight();
		int companyLogoWidth = companyLogoImage.getWidth();

		String realCompanyLogo = companyLogo;
		int realCompanyLogoHeight = companyLogoHeight;
		int realCompanyLogoWidth = companyLogoWidth;

		// User

		User user = PortalUtil.getUser(req);

		boolean signedIn = false;

		if (user == null) {
			user = company.getDefaultUser();
		}
		else if (!user.isDefaultUser()) {
			signedIn = true;
		}

		User realUser = user;

		Long realUserId = (Long)ses.getAttribute(WebKeys.USER_ID);

		if (realUserId != null) {
			if (user.getUserId() != realUserId.longValue()) {
				realUser = UserLocalServiceUtil.getUserById(
					realUserId.longValue());
			}
		}

		String doAsUserId = ParamUtil.getString(req, "doAsUserId");

		// Permission checker

		PermissionCheckerImpl permissionChecker =
			PermissionCheckerFactory.create(user, true);

		PermissionThreadLocal.setPermissionChecker(permissionChecker);

		// Locale

		String i18nLanguageId = (String)req.getAttribute(
			WebKeys.I18N_LANGUAGE_ID);

		Locale locale = (Locale)ses.getAttribute(Globals.LOCALE_KEY);

		if (Validator.isNotNull(i18nLanguageId)) {
			locale = LocaleUtil.fromLanguageId(i18nLanguageId);
		}
		else if (locale == null) {
			if (signedIn) {
				locale = user.getLocale();
			}
			else {

				// User previously set their preferred language

				String languageId = CookieKeys.getCookie(
					req, CookieKeys.GUEST_LANGUAGE_ID);

				if (Validator.isNotNull(languageId)) {
					locale = LocaleUtil.fromLanguageId(languageId);
				}

				// Get locale from the request

				if ((locale == null) && PropsValues.LOCALE_DEFAULT_REQUEST) {
					locale = req.getLocale();
				}

				// Get locale from the default user

				if (locale == null) {
					locale = user.getLocale();
				}

				if (Validator.isNull(locale.getCountry())) {

					// Locales must contain the country code

					locale = LanguageUtil.getLocale(locale.getLanguage());
				}

				List<Locale> availableLocales = ListUtil.fromArray(
					LanguageUtil.getAvailableLocales());

				if (!availableLocales.contains(locale)) {
					locale = user.getLocale();
				}
			}

			ses.setAttribute(Globals.LOCALE_KEY, locale);

			LanguageUtil.updateCookie(res, locale);
		}

		// Cookie support

		try {

			// LEP-4069

			CookieKeys.validateSupportCookie(req);
		}
		catch (Exception e) {
			CookieKeys.addSupportCookie(res);
		}

		// Time zone

		TimeZone timeZone = user.getTimeZone();

		if (timeZone == null) {
			timeZone = company.getTimeZone();
		}

		// Layouts

		if (signedIn) {
			if (PropsValues.LAYOUT_USER_PRIVATE_LAYOUTS_ENABLED) {
				addDefaultUserPrivateLayouts(user);
			}
			else {
				deleteDefaultUserPrivateLayouts(user);
			}

			if (PropsValues.LAYOUT_USER_PUBLIC_LAYOUTS_ENABLED) {
				addDefaultUserPublicLayouts(user);
			}
			else {
				deleteDefaultUserPublicLayouts(user);
			}
		}

		Layout layout = null;
		List<Layout> layouts = null;

		long plid = ParamUtil.getLong(req, "p_l_id");

		if (plid > 0) {
			layout = LayoutLocalServiceUtil.getLayout(plid);
		}
		else {
			long groupId = ParamUtil.getLong(req, "groupId");
			boolean privateLayout = ParamUtil.getBoolean(req, "privateLayout");
			long layoutId = ParamUtil.getLong(req, "layoutId");

			if ((groupId > 0) && layoutId > 0) {
				layout = LayoutLocalServiceUtil.getLayout(
					groupId, privateLayout, layoutId);
			}
		}

		if (layout != null) {
			try {
				if (!signedIn && PropsValues.AUTH_FORWARD_BY_REDIRECT) {
					req.setAttribute(WebKeys.REQUESTED_LAYOUT, layout);
				}

				boolean isViewableCommunity = isViewableCommunity(
					user, layout.getGroupId(), layout.isPrivateLayout(),
					permissionChecker);

				if (!isViewableCommunity) {
					StringMaker sm = new StringMaker();

					sm.append("User ");
					sm.append(user.getUserId());
					sm.append(" is not allowed to access the ");
					sm.append(layout.isPrivateLayout() ? "private": "public");
					sm.append(" pages of group ");
					sm.append(layout.getGroupId());

					_log.warn(sm.toString());

					layout = null;
				}
				else if (isViewableCommunity &&
						!LayoutPermissionUtil.contains(
							permissionChecker, layout, ActionKeys.VIEW)) {

					layout = null;
				}
				else {
					layouts = LayoutLocalServiceUtil.getLayouts(
						layout.getGroupId(), layout.isPrivateLayout(),
						LayoutConstants.DEFAULT_PARENT_LAYOUT_ID);
				}
			}
			catch (NoSuchLayoutException nsle) {
			}
		}

		if (layout == null) {
			Object[] defaultLayout = getDefaultLayout(req, user, signedIn);

			layout = (Layout)defaultLayout[0];
			layouts = (List<Layout>)defaultLayout[1];

			req.setAttribute(WebKeys.LAYOUT_DEFAULT, Boolean.TRUE);
		}

		Object[] viewableLayouts = getViewableLayouts(
			req, user, permissionChecker, layout, layouts);

		String layoutSetLogo = null;

		layout = (Layout)viewableLayouts[0];
		layouts = (List<Layout>)viewableLayouts[1];

		LayoutTypePortlet layoutTypePortlet = null;

		long portletGroupId = PortalUtil.getPortletGroupId(layout);

		rememberVisitedGroupIds(req, portletGroupId);

		layouts = mergeAdditionalLayouts(
			req, user, permissionChecker, layout, layouts);

		if (layout != null) {
			if (company.isCommunityLogo()) {
				LayoutSet layoutSet = layout.getLayoutSet();

				if (layoutSet.isLogo()) {
					long logoId = layoutSet.getLogoId();

					layoutSetLogo =
						imagePath + "/layout_set_logo?img_id=" + logoId +
							"&t=" + ImageServletTokenUtil.getToken(logoId);

					Image layoutSetLogoImage =
						ImageLocalServiceUtil.getCompanyLogo(logoId);

					companyLogo = layoutSetLogo;
					companyLogoHeight = layoutSetLogoImage.getHeight();
					companyLogoWidth = layoutSetLogoImage.getWidth();
				}
			}

			plid = layout.getPlid();

			// Updates to shared layouts are not reflected until the next time
			// the user logs in because group layouts are cached in the session

			layout = (Layout)((LayoutImpl)layout).clone();

			layoutTypePortlet = (LayoutTypePortlet)layout.getLayoutType();

			LayoutClone layoutClone = LayoutCloneFactory.getInstance();

			if (layoutClone != null) {
				String typeSettings = layoutClone.get(req, plid);

				if (typeSettings != null) {
					Properties props = new SafeProperties();

					PropertiesUtil.load(props, typeSettings);

					String stateMax = props.getProperty(
						LayoutTypePortletImpl.STATE_MAX);
					String stateMin = props.getProperty(
						LayoutTypePortletImpl.STATE_MIN);
					String modeAbout = props.getProperty(
						LayoutTypePortletImpl.MODE_ABOUT);
					String modeConfig = props.getProperty(
						LayoutTypePortletImpl.MODE_CONFIG);
					String modeEdit = props.getProperty(
						LayoutTypePortletImpl.MODE_EDIT);
					String modeEditDefaults = props.getProperty(
						LayoutTypePortletImpl.MODE_EDIT_DEFAULTS);
					String modeEditGuest = props.getProperty(
						LayoutTypePortletImpl.MODE_EDIT_GUEST);
					String modeHelp = props.getProperty(
						LayoutTypePortletImpl.MODE_HELP);
					String modePreview = props.getProperty(
						LayoutTypePortletImpl.MODE_PREVIEW);
					String modePrint = props.getProperty(
						LayoutTypePortletImpl.MODE_PRINT);

					layoutTypePortlet.setStateMax(stateMax);
					layoutTypePortlet.setStateMin(stateMin);
					layoutTypePortlet.setModeAbout(modeAbout);
					layoutTypePortlet.setModeConfig(modeConfig);
					layoutTypePortlet.setModeEdit(modeEdit);
					layoutTypePortlet.setModeEditDefaults(modeEditDefaults);
					layoutTypePortlet.setModeEditGuest(modeEditGuest);
					layoutTypePortlet.setModeHelp(modeHelp);
					layoutTypePortlet.setModePreview(modePreview);
					layoutTypePortlet.setModePrint(modePrint);
				}
			}

			req.setAttribute(WebKeys.LAYOUT, layout);
			req.setAttribute(WebKeys.LAYOUTS, layouts);

			if (layout.isPrivateLayout()) {
				permissionChecker.setCheckGuest(false);
			}
		}

		// Theme and color scheme

		Theme theme = null;
		ColorScheme colorScheme = null;

		boolean wapTheme = BrowserSnifferUtil.is_wap(req);

		if (layout != null) {
			if (wapTheme) {
				theme = layout.getWapTheme();
				colorScheme = layout.getWapColorScheme();
			}
			else {
				theme = layout.getTheme();
				colorScheme = layout.getColorScheme();
			}
		}
		else {
			String themeId = null;
			String colorSchemeId = null;

			if (wapTheme) {
				themeId = ThemeImpl.getDefaultWapThemeId();
				colorSchemeId = ColorSchemeImpl.getDefaultWapColorSchemeId();
			}
			else {
				themeId = ThemeImpl.getDefaultRegularThemeId();
				colorSchemeId =
					ColorSchemeImpl.getDefaultRegularColorSchemeId();
			}

			theme = ThemeLocalServiceUtil.getTheme(
				companyId, themeId, wapTheme);
			colorScheme = ThemeLocalServiceUtil.getColorScheme(
				companyId, theme.getThemeId(), colorSchemeId, wapTheme);
		}

		req.setAttribute(WebKeys.THEME, theme);
		req.setAttribute(WebKeys.COLOR_SCHEME, colorScheme);

		boolean themeCssFastLoad = ParamUtil.getBoolean(
			req, "css_fast_load", PropsValues.THEME_CSS_FAST_LOAD);

		boolean themeJsBarebone = PropsValues.JAVASCRIPT_BAREBONE_ENABLED;

		if (themeJsBarebone) {
			if (signedIn) {
				themeJsBarebone = false;
			}
		}

		boolean themeJsFastLoad = ParamUtil.getBoolean(
			req, "js_fast_load", PropsValues.JAVASCRIPT_FAST_LOAD);

		String lifecycle = ParamUtil.getString(req, "p_p_lifecycle");

		String facebookCanvasPageURL = (String)req.getAttribute(
			WebKeys.FACEBOOK_CANVAS_PAGE_URL);

		boolean widget = false;

		Boolean widgetObj = (Boolean)req.getAttribute(WebKeys.WIDGET);

		if (widgetObj != null) {
			widget = widgetObj.booleanValue();
		}

		// Theme display

		ThemeDisplay themeDisplay = ThemeDisplayFactory.create();

		// Set the CDN host, portal URL, and Facebook application ID first
		// because other methods (setLookAndFeel) depend on them being set

		themeDisplay.setCDNHost(cdnHost);
		themeDisplay.setPortalURL(portalURL);
		themeDisplay.setFacebookCanvasPageURL(facebookCanvasPageURL);
		themeDisplay.setWidget(widget);

		themeDisplay.setCompany(company);
		themeDisplay.setCompanyLogo(companyLogo);
		themeDisplay.setCompanyLogoHeight(companyLogoHeight);
		themeDisplay.setCompanyLogoWidth(companyLogoWidth);
		themeDisplay.setRealCompanyLogo(realCompanyLogo);
		themeDisplay.setRealCompanyLogoHeight(realCompanyLogoHeight);
		themeDisplay.setRealCompanyLogoWidth(realCompanyLogoWidth);
		themeDisplay.setUser(user);
		themeDisplay.setRealUser(realUser);
		themeDisplay.setDoAsUserId(doAsUserId);
		themeDisplay.setLayoutSetLogo(layoutSetLogo);
		themeDisplay.setLayout(layout);
		themeDisplay.setLayouts(layouts);
		themeDisplay.setPlid(plid);
		themeDisplay.setLayoutTypePortlet(layoutTypePortlet);
		themeDisplay.setPortletGroupId(portletGroupId);
		themeDisplay.setSignedIn(signedIn);
		themeDisplay.setPermissionChecker(permissionChecker);
		themeDisplay.setLocale(locale);
		themeDisplay.setLanguageId(LocaleUtil.toLanguageId(locale));
		themeDisplay.setI18nLanguageId(i18nLanguageId);
		themeDisplay.setTimeZone(timeZone);
		themeDisplay.setLookAndFeel(contextPath, theme, colorScheme);
		themeDisplay.setThemeCssFastLoad(themeCssFastLoad);
		themeDisplay.setThemeJsBarebone(themeJsBarebone);
		themeDisplay.setThemeJsFastLoad(themeJsFastLoad);
		themeDisplay.setServerName(req.getServerName());
		themeDisplay.setServerPort(req.getServerPort());
		themeDisplay.setSecure(req.isSecure());
		themeDisplay.setLifecycle(lifecycle);
		themeDisplay.setLifecycleAction(lifecycle.equals("1"));
		themeDisplay.setLifecycleRender(lifecycle.equals("0"));
		themeDisplay.setLifecycleResource(lifecycle.equals("2"));
		themeDisplay.setStateExclusive(LiferayWindowState.isExclusive(req));
		themeDisplay.setStateMaximized(LiferayWindowState.isMaximized(req));
		themeDisplay.setStatePopUp(LiferayWindowState.isPopUp(req));
		themeDisplay.setPathApplet(contextPath + "/applets");
		themeDisplay.setPathCms(contextPath + "/cms");
		themeDisplay.setPathContext(contextPath);
		themeDisplay.setPathFlash(contextPath + "/flash");
		themeDisplay.setPathFriendlyURLPrivateGroup(
			friendlyURLPrivateGroupPath);
		themeDisplay.setPathFriendlyURLPrivateUser(friendlyURLPrivateUserPath);
		themeDisplay.setPathFriendlyURLPublic(friendlyURLPublicPath);
		themeDisplay.setPathImage(imagePath);
		themeDisplay.setPathJavaScript(cdnHost + contextPath + "/html/js");
		themeDisplay.setPathMain(mainPath);
		themeDisplay.setPathSound(contextPath + "/html/sound");

		// URLs

		themeDisplay.setShowAddContentIcon(false);
		themeDisplay.setShowHomeIcon(true);
		themeDisplay.setShowMyAccountIcon(signedIn);
		themeDisplay.setShowPageSettingsIcon(false);
		themeDisplay.setShowPortalIcon(true);
		themeDisplay.setShowSignInIcon(!signedIn);
		themeDisplay.setShowSignOutIcon(signedIn);
		themeDisplay.setShowStagingIcon(false);

		PortletURL createAccountURL = new PortletURLImpl(
			req, PortletKeys.MY_ACCOUNT, plid, PortletRequest.ACTION_PHASE);

		createAccountURL.setWindowState(WindowState.MAXIMIZED);
		createAccountURL.setPortletMode(PortletMode.VIEW);

		createAccountURL.setParameter("saveLastPath", "0");
		createAccountURL.setParameter(
			"struts_action", "/my_account/create_account");

		themeDisplay.setURLCreateAccount(createAccountURL);

		String currentURL = PortalUtil.getCurrentURL(req);

		themeDisplay.setURLCurrent(currentURL);

		String urlHome = portalURL + contextPath;

		themeDisplay.setURLHome(urlHome);

		if (layout != null) {
			Group group = layout.getGroup();

			if (layout.getType().equals(LayoutConstants.TYPE_PORTLET)) {
				boolean freeformLayout =
					layoutTypePortlet.getLayoutTemplateId().equals(
						"freeform");

				themeDisplay.setFreeformLayout(freeformLayout);

				boolean hasUpdateLayoutPermission =
					LayoutPermissionUtil.contains(
						permissionChecker, layout, ActionKeys.UPDATE);

				if (hasUpdateLayoutPermission) {
					if (!LiferayWindowState.isMaximized(req)) {
						themeDisplay.setShowAddContentIcon(true);
					}

					themeDisplay.setShowLayoutTemplatesIcon(true);

					themeDisplay.setURLAddContent(
						"LayoutConfiguration.toggle('" + plid + "', '" +
							PortletKeys.LAYOUT_CONFIGURATION + "', '" +
								doAsUserId + "');");

					themeDisplay.setURLLayoutTemplates(
						"showLayoutTemplates();");
				}
			}

			boolean hasManageLayoutsPermission =
				GroupPermissionUtil.contains(
					permissionChecker, portletGroupId,
					ActionKeys.MANAGE_LAYOUTS);

			if (group.isUser()) {
				if ((layout.isPrivateLayout() &&
					 !PropsValues.LAYOUT_USER_PRIVATE_LAYOUTS_MODIFIABLE) ||
					(layout.isPublicLayout() &&
					 !PropsValues.LAYOUT_USER_PUBLIC_LAYOUTS_MODIFIABLE)) {

					hasManageLayoutsPermission = false;
				}
			}

			if (hasManageLayoutsPermission) {
				themeDisplay.setShowPageSettingsIcon(true);

				PortletURL pageSettingsURL = new PortletURLImpl(
					req, PortletKeys.LAYOUT_MANAGEMENT, plid,
					PortletRequest.RENDER_PHASE);

				pageSettingsURL.setWindowState(WindowState.MAXIMIZED);
				pageSettingsURL.setPortletMode(PortletMode.VIEW);

				pageSettingsURL.setParameter(
					"struts_action", "/layout_management/edit_pages");

				if (layout.isPrivateLayout()) {
					pageSettingsURL.setParameter("tabs1", "private-pages");
				}
				else {
					pageSettingsURL.setParameter("tabs1", "public-pages");
				}

				pageSettingsURL.setParameter("redirect", currentURL);
				pageSettingsURL.setParameter(
					"groupId", String.valueOf(portletGroupId));
				pageSettingsURL.setParameter("selPlid", String.valueOf(plid));

				themeDisplay.setURLPageSettings(pageSettingsURL);

				PortletURL publishToLiveURL = new PortletURLImpl(
					req, PortletKeys.LAYOUT_MANAGEMENT, plid,
					PortletRequest.RENDER_PHASE);

				publishToLiveURL.setWindowState(LiferayWindowState.EXCLUSIVE);
				publishToLiveURL.setPortletMode(PortletMode.VIEW);

				publishToLiveURL.setParameter(
					"struts_action", "/layout_management/export_pages");

				if (layout.isPrivateLayout()) {
					publishToLiveURL.setParameter("tabs1", "private-pages");
				}
				else {
					publishToLiveURL.setParameter("tabs1", "public-pages");
				}

				publishToLiveURL.setParameter("pagesRedirect", currentURL);
				publishToLiveURL.setParameter(
					"groupId", String.valueOf(portletGroupId));
				publishToLiveURL.setParameter("selPlid", String.valueOf(plid));

				themeDisplay.setURLPublishToLive(publishToLiveURL);
			}

			if (group.hasStagingGroup() && !group.isStagingGroup()) {
				themeDisplay.setShowAddContentIcon(false);
				themeDisplay.setShowLayoutTemplatesIcon(false);
				themeDisplay.setShowPageSettingsIcon(false);
				themeDisplay.setURLPublishToLive(null);
			}

			// LEP-4987

			if (group.hasStagingGroup() || group.isStagingGroup()) {
				boolean hasApproveProposalPermission =
					GroupPermissionUtil.contains(
						permissionChecker, portletGroupId,
						ActionKeys.APPROVE_PROPOSAL);

				if (hasManageLayoutsPermission) {
					themeDisplay.setShowStagingIcon(true);
				}
				else if (hasApproveProposalPermission) {
					themeDisplay.setShowStagingIcon(true);
				}
			}

			String myAccountNamespace = PortalUtil.getPortletNamespace(
				PortletKeys.MY_ACCOUNT);

			String myAccountRedirect = ParamUtil.getString(
				req, myAccountNamespace + "backURL", currentURL);

			PortletURL myAccountURL = new PortletURLImpl(
				req, PortletKeys.MY_ACCOUNT, plid, PortletRequest.RENDER_PHASE);

			myAccountURL.setWindowState(WindowState.MAXIMIZED);
			myAccountURL.setPortletMode(PortletMode.VIEW);

			myAccountURL.setParameter("struts_action", "/my_account/edit_user");
			myAccountURL.setParameter("backURL", myAccountRedirect);

			themeDisplay.setURLMyAccount(myAccountURL);
		}

		if ((!user.isActive()) ||
			(PropsValues.TERMS_OF_USE_REQUIRED &&
			 !user.isAgreedToTermsOfUse())) {

			themeDisplay.setShowAddContentIcon(false);
			themeDisplay.setShowMyAccountIcon(false);
			themeDisplay.setShowPageSettingsIcon(false);
		}

		themeDisplay.setURLPortal(themeDisplay.getURLHome());

		String urlSignIn = mainPath + "/portal/login";

		if (layout != null) {
			urlSignIn = HttpUtil.addParameter(
				urlSignIn, "p_l_id", layout.getPlid());
		}

		themeDisplay.setURLSignIn(urlSignIn);

		themeDisplay.setURLSignOut(mainPath + "/portal/logout");

		PortletURL updateManagerURL = new PortletURLImpl(
			req, PortletKeys.UPDATE_MANAGER, plid, PortletRequest.RENDER_PHASE);

		updateManagerURL.setWindowState(WindowState.MAXIMIZED);
		updateManagerURL.setPortletMode(PortletMode.VIEW);

		updateManagerURL.setParameter("struts_action", "/update_manager/view");

		themeDisplay.setURLUpdateManager(updateManagerURL);

		req.setAttribute(WebKeys.THEME_DISPLAY, themeDisplay);

		// Parallel render

		boolean parallelRenderEnable = true;

		if (layout != null) {
			if (layoutTypePortlet.getPortletIds().size() == 1) {
				parallelRenderEnable = false;
			}
		}

		Boolean parallelRenderEnableObj = Boolean.valueOf(
			ParamUtil.getBoolean(req, "p_p_parallel", parallelRenderEnable));

		req.setAttribute(
			WebKeys.PORTLET_PARALLEL_RENDER, parallelRenderEnableObj);
	}

	protected File privateLARFile;
	protected File publicLARFile;

	private static final String _PATH_PORTAL_LAYOUT = "/portal/layout";

	private static Log _log = LogFactory.getLog(ServicePreAction.class);

}