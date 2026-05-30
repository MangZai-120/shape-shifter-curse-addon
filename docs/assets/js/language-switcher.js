(function () {
  "use strict";

  // 站内英文镜像目录前缀（相对于站点根，docs/en/ 对应 /en/）
  var EN_PREFIX = "en/";

  function isOnEnglishPage(pathname) {
    // 通过相对的 /en/ 段判断；同时兼容 readthedocs 的 /zh-cn/latest/en/ 形式
    return /(^|\/)en\//.test(pathname);
  }

  // 计算对应中/英镜像页的相对跳转 URL
  function buildMirrorUrl(toEnglish) {
    var path = window.location.pathname;
    if (toEnglish) {
      // 中文页 -> 英文镜像页：在文档根之后插入 en/
      // 例如 /zh-cn/latest/sp_forms/sp_allay/  =>  /zh-cn/latest/en/sp_forms/sp_allay/
      // 本地 file:// 直接打开 site/ 时也兼容：/.../site/sp_forms/sp_allay/index.html  =>  /.../site/en/sp_forms/sp_allay/index.html
      // 策略：找到当前页所在层级相对于站点根的部分，在站点根（first segment after host or after 'latest'）后插入
      // 简化做法：基于已知 mkdocs 输出层级，定位 "/" 之间的段，插入 'en/' 到第一个非空段（local）或 'latest/' 之后（rtd）。
      if (/\/latest\//.test(path)) {
        return path.replace(/\/latest\//, "/latest/" + EN_PREFIX);
      }
      // 本地或自定义部署：把 'en/' 插到首段之后（即站点根）
      // 找到 path 里 mkdocs 实际输出根：通常是 site/ 或 / 直接是根
      var siteIdx = path.indexOf("/site/");
      if (siteIdx >= 0) {
        return path.slice(0, siteIdx + 6) + EN_PREFIX + path.slice(siteIdx + 6);
      }
      // 兜底：直接在 / 后插 en/
      return "/" + EN_PREFIX + path.replace(/^\//, "");
    } else {
      // 英文页 -> 中文：去掉 en/ 段
      return path.replace(/(\/)en\//, "$1");
    }
  }

  function openMirrorPage() {
    var goEnglish = !isOnEnglishPage(window.location.pathname);
    var target = buildMirrorUrl(goEnglish);
    if (window.location.hash) target += window.location.hash;
    window.location.href = target;
  }

  function createButton() {
    var button = document.createElement("button");
    var icon = document.createElement("i");
    var label = document.createElement("span");

    var onEn = isOnEnglishPage(window.location.pathname);
    button.type = "button";
    button.className = "ssca-language-switch";
    button.title = onEn ? "切换为中文" : "Switch to English";
    button.setAttribute("aria-label", button.title);

    icon.className = "fa fa-language";
    icon.setAttribute("aria-hidden", "true");
    label.textContent = onEn ? "中文" : "English";

    button.appendChild(icon);
    button.appendChild(label);
    button.addEventListener("click", openMirrorPage);

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