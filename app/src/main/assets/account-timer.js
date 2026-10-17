(function () {
  if (!window.__yatoriConcurrencyHiddenInstalled) {
    window.__yatoriConcurrencyHiddenInstalled = true

    function hideLegacyConcurrencyCard() {
      document.querySelectorAll(".card, section, [class*='card']").forEach((node) => {
        const title = node.querySelector?.(".card-title, .section-header, h2, h3")
        if (title?.textContent?.trim() !== "任务并发") return
        node.hidden = true
        node.setAttribute("aria-hidden", "true")
        node.style.setProperty("display", "none", "important")
      })
    }

    hideLegacyConcurrencyCard()
    new MutationObserver(hideLegacyConcurrencyCard).observe(document.documentElement, {
      childList: true,
      subtree: true,
    })
  }

  const bridge = window.YatoriAndroidBridge
  if (!bridge || window.__yatoriAccountTimerInstalled) return
  window.__yatoriAccountTimerInstalled = true

  const originalCall = bridge.call
  const originalCallAsync = bridge.callAsync
  const durations = new Map()
  let currentMinutes = 0

  function accountKey(platform, account) {
    return `${platform}\u0000${account}`
  }

  function minutes(value) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : 0
  }

  function refreshDurations() {
    if (typeof originalCall !== "function") return
    try {
      const response = JSON.parse(originalCall.call(bridge, "ListAccounts", "[]"))
      if (!response.ok || !Array.isArray(response.data)) return
      durations.clear()
      response.data.forEach((account) => {
        durations.set(
          accountKey(account.accountType, account.account),
          minutes(account.runDurationMinutes),
        )
      })
    } catch (_) {
      // The core may still be initializing when the page first loads.
    }
  }

  function mountField() {
    const modal = document.querySelector(".account-modal")
    const anchor = modal?.querySelector(".account-modal-body .form-row-2")
    if (!anchor) return

    let group = modal.querySelector(".yatori-run-duration")
    if (!group) {
      group = document.createElement("div")
      group.className = "form-group yatori-run-duration"

      const label = document.createElement("label")
      label.className = "form-label"
      label.htmlFor = "yatori-run-duration-input"
      label.textContent = "任务定时"

      const input = document.createElement("input")
      input.id = "yatori-run-duration-input"
      input.className = "form-input"
      input.type = "number"
      input.min = "0"
      input.step = "1"
      input.setAttribute("aria-label", "任务定时（分钟，0 为不限时）")
      input.addEventListener("input", () => {
        currentMinutes = minutes(input.value)
      })

      group.append(label, input)
      anchor.insertAdjacentElement("afterend", group)
    }

    const input = group.querySelector("input")
    if (input && document.activeElement !== input) {
      input.value = String(currentMinutes)
    }
  }

  function withDuration(method, payload) {
    if (method !== "AddAccount" && method !== "UpdateAccount") return payload
    try {
      const args = JSON.parse(payload)
      if (!Array.isArray(args) || !args[0] || typeof args[0] !== "object") return payload
      const input = document.getElementById("yatori-run-duration-input")
      args[0].runDurationMinutes = minutes(input ? input.value : currentMinutes)
      return JSON.stringify(args)
    } catch (_) {
      return payload
    }
  }

  function scheduleRefresh(method) {
    if (method === "AddAccount" || method === "UpdateAccount") {
      window.setTimeout(refreshDurations, 600)
    }
  }

  // Java-injected objects are not reliably writable on every WebView. Keep
  // the native object intact and expose a plain JS forwarding facade instead.
  // The React bundle reads window.YatoriAndroidBridge on every request, so
  // both synchronous and asynchronous calls pass through withDuration().
  const wrappedBridge = {}
  if (typeof originalCall === "function") {
    wrappedBridge.call = (method, payload) => {
      const result = originalCall.call(bridge, method, withDuration(method, payload))
      scheduleRefresh(method)
      return result
    }
  }
  if (typeof originalCallAsync === "function") {
    wrappedBridge.callAsync = (method, payload, callbackId) => {
      const result = originalCallAsync.call(bridge, method, withDuration(method, payload), callbackId)
      scheduleRefresh(method)
      return result
    }
  }
  if (typeof bridge.getSystemTheme === "function") {
    wrappedBridge.getSystemTheme = (...args) => bridge.getSystemTheme(...args)
  }
  window.YatoriAndroidBridge = wrappedBridge

  document.addEventListener("click", (event) => {
    const button = event.target instanceof Element ? event.target.closest("button") : null
    if (!button) return

    if (button.textContent.includes("添加账号")) {
      currentMinutes = 0
      return
    }

    if (!button.textContent.includes("编辑")) return
    const row = button.closest("tr")
    const platform = row?.querySelector('[data-label="平台"]')?.textContent.trim()
    const account = row?.querySelector('[data-label="账号"]')?.textContent.trim()
    if (!platform || !account) return

    refreshDurations()
    currentMinutes = durations.get(accountKey(platform, account)) ?? 0
  }, true)

  const observer = new MutationObserver(() => {
    hideLegacyConcurrencyCard()
    mountField()
  })
  observer.observe(document.documentElement, {
    childList: true,
    subtree: true,
  })

  window.setTimeout(() => {
    hideLegacyConcurrencyCard()
    refreshDurations()
  }, 500)
})()
