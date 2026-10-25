// Loaded first, as a plain script: says the page failed when one of its scripts can't load or
// throws before office.js can say how it went (a WebView too old for import maps, say).
function failed() {
  if (document.title !== "shown") document.title = "failed";
}
window.addEventListener("error", failed);
window.addEventListener("unhandledrejection", failed);
document.addEventListener("error", (event) => {
  if (event.target instanceof HTMLScriptElement) failed();
}, true);
