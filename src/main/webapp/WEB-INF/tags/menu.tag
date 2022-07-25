<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@taglib prefix="uv" tagdir="/WEB-INF/tags" %>
<%@taglib prefix="icon" tagdir="/WEB-INF/tags/icons" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<spring:url var="URL_PREFIX" value="/web"/>

<div class="navigation print:tw-hidden">
    <div class="tw-relative">
        <input id="menu-toggle-checkbox" type="checkbox" class="tw-hidden">
        <label for="menu-toggle-checkbox" class="tw-flex tw-items-center tw-m-0 tw-px-4 tw-py-4 tw-absolute tw-top-0 tw-left-0 lg:tw-hidden tw-cursor-pointer">
            <span class="tw-inline-block">
                <span class="hamburger-bar"></span>
                <span class="hamburger-bar"></span>
                <span class="hamburger-bar"></span>
                <span class="hamburger-bar"></span>
                <span class="hamburger-bar"></span>
            </span>
        </label>
        <nav class="tw-max-w-6xl tw-mx-auto">
            <div class="navigation-inner tw-flex tw-w-full">
                <div class="tw-order-last" role="menubar">
                    <div class="tw-p-3 lg:tw-p-2 tw-flex tw-items-center tw-space-x-2">
                        <c:choose>
                            <c:when test="${navigationRequestPopupEnabled}">
                                <div class="tw-relative">
                                    <button
                                        id="add-something-new"
                                        class="nav-popup-menu-button tw-border-none tw-text-base tw-bg-transparent tw-flex tw-items-center"
                                        aria-expanded="false"
                                        aria-haspopup="true"
                                        data-test-id="add-something-new"
                                    >
                                        <icon:plus className="nav-popup-menu-button_icon tw-w-7 tw-h-7" />
                                        <span class="tw-sr-only">
                                            <spring:message code="nav.add.button.text" />
                                        </span>
                                        <span class="dropdown-caret tw-ml-0.5 tw-mt-0.5"></span>
                                    </button>
                                    <div
                                        id="add-something-new-menu"
                                        class="nav-popup-menu tw-w-screen tw-absolute tw-right-2 tw-top-full tw-mt-1 tw-flex tw-justify-end tw-origin-top-right tw-transition-transform tw-scale-x-0 tw-scale-y-0 tw-scale-x-1 tw-scale-y-1"
                                        aria-hidden="true"
                                        data-test-id="add-something-new-popupmenu"
                                    >
                                        <div class="tw-inline-block tw-py-1 tw-rounded nav-popup-menu-inner tw-backdrop-blur tw-bg-zinc-900 supports-backdrop-blur:tw-bg-zinc-900/[0.85] dark:tw-border dark:tw-border-zinc-700">
                                            <ul
                                                class="tw-list-none tw-m-0 tw-p-0"
                                                role="menu"
                                            >
                                                <li role="none">
                                                    <a
                                                        href="${URL_PREFIX}/application/new"
                                                        role="menuitem"
                                                        class="nav-popup-menu_link tw-block tw-py-2 tw-px-3 tw-no-underline tw-flex tw-items-center"
                                                        data-test-id="quick-add-new-application"
                                                    >
                                                        <span class="nav-popup-menu_link-icon tw-px-2 tw-py-1 tw-rounded tw-flex tw-items-center">
                                                            <icon:calendar className="tw-h-5 tw-w-5" />
                                                        </span>
                                                        <span class="tw-ml-3">
                                                            <spring:message code="nav.add.vacation" />
                                                        </span>
                                                    </a>
                                                </li>
                                                <c:if test="${navigationSickNoteAddAccess}">
                                                    <li role="none">
                                                        <a
                                                            href="${URL_PREFIX}/sicknote/new"
                                                            role="menuitem"
                                                            class="nav-popup-menu_link tw-block tw-py-2 tw-px-3 tw-no-underline tw-flex tw-items-center"
                                                            data-test-id="quick-add-new-sicknote"
                                                        >
                                                            <span class="nav-popup-menu_link-icon tw-px-2 tw-py-1 tw-rounded tw-flex tw-items-center">
                                                                <icon:medkit className="tw-h-5 tw-w-5" />
                                                            </span>
                                                            <span class="tw-ml-3">
                                                                <spring:message code="nav.add.sicknote" />
                                                            </span>
                                                        </a>
                                                    </li>
                                                </c:if>
                                                <c:if test="${navigationOvertimeItemEnabled}">
                                                    <li role="none">
                                                        <a
                                                            href="${URL_PREFIX}/overtime/new"
                                                            role="menuitem"
                                                            class="nav-popup-menu_link tw-block tw-py-2 tw-px-3 tw-no-underline tw-flex tw-items-center"
                                                            data-test-id="quick-add-new-overtime"
                                                        >
                                                            <span class="nav-popup-menu_link-icon tw-px-2 tw-py-1 tw-rounded tw-flex tw-items-center">
                                                                <icon:briefcase className="tw-h-5 tw-w-5" />
                                                            </span>
                                                            <span class="tw-ml-3">
                                                                <spring:message code="nav.add.overtime" />
                                                            </span>
                                                        </a>
                                                    </li>
                                                </c:if>
                                            </ul>
                                        </div>
                                    </div>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <a
                                    href="${URL_PREFIX}/application/new"
                                    class="tw-flex tw-items-center"
                                    data-test-id="new-application"
                                >
                                    <icon:plus className="nav-popup-menu-button_icon tw-w-7 tw-h-7" />
                                    <span class="tw-sr-only">
                                        <spring:message code="nav.add.vacation" />
                                    </span>
                                </a>
                            </c:otherwise>
                        </c:choose>
                        <div class="tw-relative">
                            <a
                                href="#avatar-menu"
                                class="nav-popup-menu-button tw-no-underline"
                                id="avatar-link"
                                aria-expanded="false"
                                aria-haspopup="true"
                                data-test-id="avatar"
                            >
                                <img
                                    src="<c:out value='${menuGravatarUrl}?d=mm'/>"
                                    alt=""
                                    class="gravatar tw-rounded-full"
                                    width="24px"
                                    height="24px"
                                    onerror="this.src !== '/images/gravatar.jpg' && (this.src = '/images/gravatar.jpg')"
                                />
                                <span class="dropdown-caret tw-ml-0.5 tw-mt-0.5"></span>
                            </a>
                            <div
                                id="avatar-menu"
                                class="nav-popup-menu tw-w-screen tw-absolute tw-right-1 tw-top-full tw-mt-2 tw-flex tw-justify-end tw-origin-top-right tw-transition-transform tw-scale-x-0 tw-scale-y-0 tw-scale-x-1 tw-scale-y-1"
                                aria-hidden="true"
                                data-test-id="avatar-popupmenu"
                            >
                                <div
                                    class="tw-inline-block tw-py-1 tw-rounded nav-popup-menu-inner"
                                    style="max-width: 78%"
                                >
                                    <ul class="tw-list-none tw-m-0 tw-p-0 tw-max-w-xs" role="menu">
                                        <li role="none" class="tw-mb-4">
                                            <a href="${URL_PREFIX}/person/${userId}/overview" role="menuitem" class="nav-popup-menu_link--no-hover tw-block tw-py-2 tw-pl-5 tw-pr-5 tw-no-underline tw-px-2">
                                                <span class="tw-flex tw-items-center">
                                                    <img
                                                        src="<c:out value='${menuGravatarUrl}?d=mm&s=128'/>"
                                                        alt=""
                                                        class="gravatar tw-rounded-full"
                                                        width="64px"
                                                        height="64px"
                                                        onerror="this.src !== '/images/gravatar.jpg' && (this.src = '/images/gravatar.jpg')"
                                                    />
                                                    <span class="tw-ml-6 tw-flex-1 tw-flex tw-flex-col tw-font-bold">
                                                        <span class="tw-sr-only"><spring:message code="nav.user.title" /></span>
                                                        <span>${userFirstName}</span>
                                                        <span>${userLastName}</span>
                                                    </span>
                                                </span>
                                            </a>
                                        </li>
                                        <li role="none" class="tw-mb-1">
                                            <a
                                                href="${menuHelpUrl}"
                                                role="menuitem"
                                                class="nav-popup-menu_link tw-w-full tw-block tw-py-2 tw-pl-5 tw-pr-16 tw-text-sm tw-no-underline tw-flex tw-items-center"
                                                target="_blank"
                                                rel="noopener"
                                            >
                                                <span class="nav-popup-menu_link-icon tw-px-2 tw-py-1 tw-rounded tw-flex tw-items-center tw-ml-2.5">
                                                    <icon:question-mark-circle className="tw-h-6 tw-w-6" />
                                                </span>
                                                <span class="tw-ml-4 tw-text-base">
                                                    <spring:message code="nav.help.title" />
                                                </span>
                                                <icon:external-link className="tw-ml-1.5 tw-h-4 tw-w-4" />
                                            </a>
                                        </li>
                                        <li role="none" class="tw-mb-1">
                                            <a href="${URL_PREFIX}/person/${userId}"
                                               role="menuitem"
                                               class="nav-popup-menu_link tw-w-full tw-block tw-py-2 tw-pl-5 tw-pr-16 tw-text-sm tw-no-underline tw-flex tw-items-center">
                                                <span class="nav-popup-menu_link-icon tw-px-2 tw-py-1 tw-rounded tw-flex tw-items-center tw-ml-2.5">
                                                    <icon:user-circle className="tw-h-6 tw-w-6" />
                                                </span>
                                                <span class="tw-ml-4 tw-text-base">
                                                    <spring:message code="nav.account.title" />
                                                </span>
                                            </a>
                                        </li>
                                        <li role="none" class="tw-mb-1">
                                            <a href="${URL_PREFIX}/person/${userId}/settings"
                                               role="menuitem"
                                               class="nav-popup-menu_link tw-w-full tw-block tw-py-2 tw-pl-5 tw-pr-16 tw-text-sm tw-no-underline tw-flex tw-items-center">
                                                <span class="nav-popup-menu_link-icon tw-px-2 tw-py-1 tw-rounded tw-flex tw-items-center tw-ml-2.5">
                                                    <icon:adjustments className="tw-h-6 tw-w-6" />
                                                </span>
                                                <span class="tw-ml-4 tw-text-base">
                                                    <spring:message code="nav.user-settings.title" />
                                                </span>
                                            </a>
                                        </li>
                                        <li role="none" class="tw-mb-1">
                                            <form:form action="/logout" method="POST" cssClass="tw-ml-auto tw-w-full">
                                                <button
                                                    role="menuitem"
                                                    type="submit"
                                                    class="nav-popup-menu_link tw-w-full tw-block tw-py-2 tw-pl-5 tw-pr-16 tw-text-sm tw-no-underline tw-flex tw-items-center"
                                                    data-test-id="logout"
                                                >
                                                    <span class="tw-flex tw-items-center">
                                                        <span class="nav-popup-menu_link-icon tw-px-2 tw-py-1 tw-rounded tw-flex tw-items-center tw-ml-2.5">
                                                            <icon:logout className="tw-w-6 tw-h-6" />
                                                        </span>
                                                        <span class="tw-ml-4 tw-text-base">
                                                            <spring:message code="nav.signout.title"/>
                                                        </span>
                                                    </span>
                                                </button>
                                            </form:form>
                                        </li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="tw-flex-1 navigation-items tw-mt-12 lg:tw-mt-0 tw-max-h-0 lg:tw-max-h-full">
                    <ul class="tw-list-none tw-m-0 tw-py-3 tw-px-5 tw-flex tw-flex-col tw-space-y-4 lg:tw-flex-row lg:tw-space-y-0 lg:tw-space-x-8 lg:tw-px-2 xl:tw-px-0">
                        <li class="tw-flex tw-items-center">
                            <a href="${URL_PREFIX}/overview" id="home-link" class="navigation-link tw-inline-block tw-no-underline tw-text-lg lg:tw-text-base">
                                <span class="tw-flex tw-items-center">
                                    <icon:home className="navigation-link-icon tw-w-6 tw-h-6 lg:tw-w-4 lg:tw-h-4" />
                                    <span class="tw-ml-5 lg:tw-ml-2 lg:tw-sr-only xl:tw-not-sr-only xl:tw-ml-2">
                                        <spring:message code="nav.home.title"/>
                                    </span>
                                </span>
                            </a>
                        </li>
                        <li class="tw-flex tw-items-center">
                            <a href="${URL_PREFIX}/application/new" id="application-new-link" class="navigation-link tw-inline-block tw-no-underline tw-text-lg lg:tw-text-base">
                                <span class="tw-flex tw-items-center">
                                    <icon:plus-circle className="navigation-link-icon tw-w-6 tw-h-6 lg:tw-w-4 lg:tw-h-4" />
                                    <span class="tw-ml-5 lg:tw-ml-2">
                                        <spring:message code="nav.apply.title"/>
                                    </span>
                                </span>
                            </a>
                        </li>
                        <li class="tw-flex tw-items-center">
                            <a href="${URL_PREFIX}/application" class="navigation-link tw-inline-block tw-no-underline tw-text-lg lg:tw-text-base">
                                <span class="tw-flex tw-items-center">
                                    <icon:calendar className="navigation-link-icon tw-w-6 tw-h-6 lg:tw-w-4 lg:tw-h-4" />
                                    <span class="tw-ml-5 lg:tw-ml-2">
                                        <spring:message code="nav.vacation.title"/>
                                    </span>
                                </span>
                            </a>
                        </li>
                        <c:if test="${navigationSickNoteStatisticsAccess}">
                        <li class="tw-flex tw-items-center">
                            <a href="${URL_PREFIX}/sicknote" class="navigation-link tw-inline-block tw-no-underline tw-text-lg lg:tw-text-base" data-test-id="navigation-sick-notes-link">
                                <span class="tw-flex tw-items-center">
                                    <icon:medkit className="navigation-link-icon tw-w-6 tw-h-6 lg:tw-w-4 lg:tw-h-4" />
                                    <span class="tw-ml-5 lg:tw-ml-2">
                                        <spring:message code="nav.sicknote.title"/>
                                    </span>
                                </span>
                            </a>
                        </li>
                        </c:if>
                        <c:if test="${navigationPersonListAccess}">
                        <li class="tw-flex tw-items-center">
                            <a href="${URL_PREFIX}/person?active=true" class="navigation-link tw-inline-block tw-no-underline tw-text-lg lg:tw-text-base">
                                <span class="tw-flex tw-items-center">
                                    <icon:user className="navigation-link-icon tw-w-6 tw-h-6 lg:tw-w-4 lg:tw-h-4" />
                                    <span class="tw-ml-5 lg:tw-ml-2">
                                        <spring:message code="nav.person.title"/>
                                    </span>
                                </span>
                            </a>
                        </li>
                        </c:if>
                        <c:if test="${navigationDepartmentAccess}">
                        <li class="tw-flex tw-items-center">
                            <a href="${URL_PREFIX}/department" class="navigation-link tw-inline-block tw-no-underline tw-text-lg lg:tw-text-base">
                                <span class="tw-flex tw-items-center">
                                    <icon:user-group className="navigation-link-icon tw-w-6 tw-h-6 lg:tw-w-4 lg:tw-h-4" />
                                    <span class="tw-ml-5 lg:tw-ml-2">
                                        <spring:message code="nav.department.title"/>
                                    </span>
                                </span>
                            </a>
                        </li>
                        </c:if>
                        <c:if test="${navigationSettingsAccess}">
                        <li class="tw-flex tw-items-center">
                            <a
                                href="${URL_PREFIX}/settings"
                                class="navigation-link tw-inline-block tw-no-underline tw-text-lg lg:tw-text-base"
                                data-test-id="navigation-settings-link"
                            >
                                <span class="tw-flex tw-items-center">
                                    <icon:cog className="navigation-link-icon tw-w-6 tw-h-6 lg:tw-w-4 lg:tw-h-4" />
                                    <span class="tw-ml-5 lg:tw-ml-2">
                                        <spring:message code="nav.settings.title"/>
                                    </span>
                                </span>
                            </a>
                        </li>
                        </c:if>
                    </ul>
                </div>
            </div>
            <div class="navigation-content-spacer tw-flex tw-justify-between tw-w-full lg:tw-hidden">
                <div class="navigation-content-spacer--left tw-h-4 tw-w-4"></div>
                <div class="navigation-content-spacer--right tw-h-4 tw-w-4"></div>
            </div>
        </nav>
    </div>
</div>
