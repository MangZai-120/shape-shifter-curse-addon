(function () {
  "use strict";

  var SITE_URL = "https://shape-shifter-curse-addon.readthedocs.io/zh-cn/latest/";

  function getCurrentPageUrl() {
    var canonical = document.querySelector('link[rel="canonical"]');

    if (canonical && canonical.href) {
      return canonical.href;
    }

    if (typeof mkdocs_page_url === "string" && mkdocs_page_url.length > 0) {
      return new URL(mkdocs_page_url, SITE_URL).href;
    }

    if (window.location.protocol === "file:") {
      return SITE_URL;
    }

    return window.location.href;
  }

  function openEnglishPage() {
    var targetUrl = getCurrentPageUrl();
    var translateUrl = new URL("https://translate.google.com/translate");

    translateUrl.searchParams.set("hl", "en");
    translateUrl.searchParams.set("sl", "zh-CN");
    translateUrl.searchParams.set("tl", "en");
    translateUrl.searchParams.set("u", targetUrl);

    window.location.href = translateUrl.href;
  }

  function createButton() {
    var button = document.createElement("button");
    var icon = document.createElement("i");
    var label = document.createElement("span");

    button.type = "button";
    button.className = "ssca-language-switch";
    button.title = "将当前页面翻译为英文";
    button.setAttribute("aria-label", "将当前页面翻译为英文");

    icon.className = "fa fa-language";
    icon.setAttribute("aria-hidden", "true");
    label.textContent = "English";

    button.appendChild(icon);
    button.appendChild(label);
    button.addEventListener("click", openEnglishPage);

    return button;
  }

  function mountButton() {
    if (document.querySelector(".ssca-language-switch")) {
      return;
    }

    var breadcrumbs = document.querySelector(".wy-breadcrumbs");
    var aside = document.querySelector(".wy-breadcrumbs-aside");

    if (!breadcrumbs) {
      return;
    }

    if (!aside) {
      aside = document.createElement("li");
      aside.className = "wy-breadcrumbs-aside";
      breadcrumbs.appendChild(aside);
    }

    aside.insertBefore(createButton(), aside.firstChild);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mountButton);
  } else {
    mountButton();
  }
})();