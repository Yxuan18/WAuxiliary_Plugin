import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;

import me.hd.wauxv.plugin.api.callback.PluginCallBack;

/**
 * /price TSLA  → 返回特斯拉最新成交价
 * /price 0700.HK → 返回腾讯控股（港交所）价格
 */
void sendPrice(String talker, String symbol) {
    // Yahoo v8 chart API：直接在 meta 内拿 regularMarketPrice
    String api = "https://query1.finance.yahoo.com/v8/finance/chart/" 
                 + symbol;

    get(api, null, new PluginCallBack.HttpCallback() {           // 异步 HTTP
        public void onSuccess(int respCode, String respContent) {
            try {
				JSONObject root   = JSON.parseObject(respContent);

				// 1) 现价与货币
				Object priceObj = JSONPath.eval(root, "$.chart.result[0].meta.regularMarketPrice");
				Object currObj  = JSONPath.eval(root, "$.chart.result[0].meta.currency");

				// 2) 昨收（可能为空）
				Object prevObj  = JSONPath.eval(root, "$.chart.result[0].meta.previousClose");

				if (priceObj == null) {
					sendText(talker, "[Price] 未找到代码 " + symbol);
					return;
				}
                sendText(talker, "[Price] " + symbol + "价格为: " + priceObj + " " + currObj);
                String changeStr = "";
                if (prevObj != null) {
                    double price = ((Number) priceObj).doubleValue();   // ← Object → double
                    double prev  = ((Number) prevObj ).doubleValue();

                    if (prev > 0) {        // 昨收必须有效
                        double diff = price - prev;
                        double pct  = diff / prev * 100.0;

                        // 为正数时手动加 “+” 号让涨跌更直观
                        changeStr = String.format(" (%s%.2f, %s%.2f%%)",
                                     diff >= 0 ? "+" : "", diff,
                                     diff >= 0 ? "+" : "", pct);
                    }
                }

				String reply = String.format("%s : %.2f %s%s",
											 symbol.toUpperCase(), price, curr, changeStr);
				sendText(talker, reply);                                  // :contentReference[oaicite:11]{index=11}
            } catch (Exception e) {
                log(e);
                sendText(talker, "[Price] 解析失败: " + e.getMessage());     // :contentReference[oaicite:12]{index=12}
            }
        }

        public void onError(Exception e) {
            log(e);
            // sendText(talker, "[Price] 请求异常: " + e.getMessage());        // :contentReference[oaicite:13]{index=13}
        }
    });
}

void onHandleMsg(Object msgInfo) {
    if (!msgInfo.isText()) return;

    String content   = msgInfo.getContent();
    String talker    = msgInfo.getTalker();      // 群号 / 私聊对方 Wxid
    boolean isGroup  = msgInfo.isGroupChat();
    String myWxid    = getLoginWxid();
    String myName    = getFriendName(myWxid);    // 群里别人 @ 我的显示名
    boolean fromMe   = msgInfo.isSend();         // 是否自己发的
    boolean mentionedMe = content.contains("@"+myName);
    boolean hasCmd   = content.contains("/price ");

    boolean shouldHandle = false;
    if (!isGroup) {
        // 私聊：对方或自己发，只要包含指令就处理
        shouldHandle = hasCmd;
    } else {
        // 群聊：自己发 or 别人 @ 我 + 指令
        shouldHandle = (fromMe && hasCmd) || (mentionedMe && hasCmd);
    }
    if (!shouldHandle) return;

    int idx = content.indexOf("/price ");
    String symbol = content.substring(idx + "/price ".length()).trim();

    if (symbol.isEmpty()) {
        String err = "股票代码不能为空，例如：/price TSLA";
        toast(err);          // 弹一条本地 Toast，免得刷屏
        log(err);            // 本地日志
        return;
    }
    sendPrice(talker, symbol);
}
