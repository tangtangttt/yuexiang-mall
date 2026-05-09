<template>
  <div class="cs-page">
    <NavBar title="AI 智能客服" :left-arrow="true" />

    <div class="cs-hero">
      <div class="cs-hero__glow" />
      <div class="cs-hero__inner">
        <div class="cs-hero__badge">
          <van-icon name="chat-o" />
          <span>7×24 在线</span>
        </div>
        <h1 class="cs-hero__title">有什么可以帮您？</h1>
        <p class="cs-hero__sub">查商品 · 看订单 · 优惠券 · 常见问题</p>
      </div>
    </div>

    <div ref="scrollWrap" class="cs-messages">
      <div v-if="!messages.length && !streaming" class="cs-empty">
        <div class="cs-empty__chips">
          <button
            v-for="(q, i) in quickQuestions"
            :key="i"
            type="button"
            class="cs-chip"
            @click="sendQuick(q)"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="cs-row"
        :class="msg.role === 'user' ? 'cs-row--user' : 'cs-row--bot'"
      >
        <div v-if="msg.role === 'assistant'" class="cs-avatar cs-avatar--bot" aria-hidden="true">
          <van-icon name="smile-o" />
        </div>
        <div class="cs-bubble-wrap">
          <div
            class="cs-bubble"
            :class="msg.role === 'user' ? 'cs-bubble--user' : 'cs-bubble--bot'"
          >
            <template v-if="msg.role === 'assistant'">
              <div v-if="msg.thinking" class="cs-thinking">
                <van-collapse v-model="msg._thinkOpen" :border="false">
                  <van-collapse-item name="1" title="思考过程">
                    <pre class="cs-thinking__pre">{{ msg.thinking }}</pre>
                  </van-collapse-item>
                </van-collapse>
              </div>
              <div class="cs-text">{{ msg.content }}</div>
              <div v-if="msg.recommend && msg.recommend.length" class="cs-rec">
                <div class="cs-rec__label">猜你想问</div>
                <div class="cs-rec__tags">
                  <span
                    v-for="(r, ri) in msg.recommend"
                    :key="ri"
                    class="cs-rec__tag"
                    @click="sendQuick(r)"
                  >{{ r }}</span>
                </div>
              </div>
            </template>
            <template v-else>
              {{ msg.content }}
            </template>
          </div>
        </div>
        <div v-if="msg.role === 'user'" class="cs-avatar cs-avatar--user" aria-hidden="true">
          <van-icon name="user-o" />
        </div>
      </div>

      <div v-if="streaming" class="cs-row cs-row--bot">
        <div class="cs-avatar cs-avatar--bot">
          <van-icon name="smile-o" />
        </div>
        <div class="cs-bubble cs-bubble--bot cs-bubble--typing">
          <span class="dot" /><span class="dot" /><span class="dot" />
        </div>
      </div>
    </div>

    <div class="cs-footer safe-bottom">
      <div class="cs-toolbar">
        <van-button
          v-if="streaming"
          size="small"
          plain
          hairline
          type="danger"
          class="cs-stop"
          @click="stopStream"
        >
          停止
        </van-button>
        <span v-else class="cs-toolbar__hint">内容由 AI 生成，请以订单与商品页为准</span>
      </div>
      <div class="cs-input-bar">
        <van-field
          v-model="input"
          rows="1"
          autosize
          type="textarea"
          maxlength="500"
          placeholder="描述您的问题…"
          :border="false"
          class="cs-field"
          @keyup.enter.native="onEnterSend"
        />
        <van-button
          round
          type="danger"
          class="cs-send"
          :disabled="!canSend"
          :loading="streaming"
          @click="send"
        >
          发送
        </van-button>
      </div>
    </div>
  </div>
</template>

<script>
import NavBar from '@/components/NavBar'
import { buildChatStreamUrl, getCustomerAuthHeader, stopCustomerStream } from '@/api/customerService'

const SESSION_KEY = 'mall_customer_ai_session_id'

export default {
  name: 'CustomerService',
  components: { NavBar },
  data() {
    return {
      sessionId: '',
      input: '',
      streaming: false,
      abortController: null,
      messages: [],
      quickQuestions: [
        '帮我搜一下热销商品',
        '查看我最近一笔订单',
        '有哪些优惠券可以领？',
        '退换货政策是怎样的？'
      ]
    }
  },
  computed: {
    canSend() {
      return this.input.trim().length > 0 && !this.streaming
    }
  },
  created() {
    try {
      this.sessionId = localStorage.getItem(SESSION_KEY) || ''
      // 如果没有 sessionId，立即生成一个新的，避免并发冲突
      if (!this.sessionId) {
        this.sessionId = this.generateSessionId()
        localStorage.setItem(SESSION_KEY, this.sessionId)
      }
    } catch (e) {
      this.sessionId = ''
    }
  },
  beforeDestroy() {
    this.abortStream()
  },
  methods: {
    generateSessionId() {
      return 'xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0
        const v = c === 'x' ? r : (r & 0x3 | 0x8)
        return v.toString(16)
      })
    },
    onEnterSend(e) {
      if (!e.shiftKey) {
        e.preventDefault()
        this.send()
      }
    },
    sendQuick(text) {
      this.input = text
      this.$nextTick(() => this.send())
    },
    abortStream() {
      if (this.abortController) {
        this.abortController.abort()
        this.abortController = null
      }
    },
    async stopStream() {
      if (this.sessionId) {
        try {
          await stopCustomerStream(this.sessionId)
        } catch (e) {
          /* ignore */
        }
      }
      this.abortStream()
      this.streaming = false
    },
    async send() {
      const q = this.input.trim()
      if (!q || this.streaming) return
      this.input = ''
      this.messages.push({ role: 'user', content: q })

      const aiIndex = this.messages.length
      this.messages.push({
        role: 'assistant',
        content: '',
        thinking: '',
        recommend: [],
        _thinkOpen: []
      })
      this.streaming = true
      this.abortController = new AbortController()
      this.$nextTick(this.scrollToBottom)

      const url = buildChatStreamUrl(this.sessionId, q)
      try {
        const res = await fetch(url, {
          method: 'GET',
          headers: {
            Accept: 'text/event-stream',
            ...getCustomerAuthHeader()
          },
          signal: this.abortController.signal
        })
        if (!res.ok) {
          throw new Error('服务暂不可用(' + res.status + ')')
        }
        await this.readSseBody(res.body, aiIndex)
      } catch (err) {
        if (err.name === 'AbortError') {
          this.patchAssistant(aiIndex, { content: (this.messages[aiIndex].content || '') + '\n（已停止）' })
        } else {
          this.patchAssistant(aiIndex, { content: '抱歉，' + (err.message || '请求失败') + '。' })
          this.$toast.fail(err.message || '请求失败')
        }
      } finally {
        this.streaming = false
        this.abortController = null
        this.$nextTick(this.scrollToBottom)
      }
    },
    patchAssistant(index, obj) {
      const m = this.messages[index]
      if (!m || m.role !== 'assistant') return
      Object.assign(m, obj)
    },
    async readSseBody(body, aiIndex) {
      const reader = body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let streamDone = false
      while (!streamDone) {
        const { done, value } = await reader.read()
        if (done) {
          streamDone = true
          break
        }
        buffer += decoder.decode(value, { stream: true })
        let nl
        while ((nl = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, nl).replace(/\r$/, '').trim()
          buffer = buffer.slice(nl + 1)
          if (line) {
            this.tryParseLine(line, aiIndex)
          }
        }
      }
      const tail = buffer.replace(/\r$/, '').trim()
      if (tail) {
        this.tryParseLine(tail, aiIndex)
      }
    },
    tryParseLine(line, aiIndex) {
      let s = line.trim()
      if (!s) return
      if (s.startsWith('data:')) {
        s = s.slice(5).trim()
      }
      if (s === '[DONE]') return
      let data
      try {
        data = JSON.parse(s)
      } catch (e) {
        return
      }
      this.applyEvent(data, aiIndex)
    },
    applyEvent(data, aiIndex) {
      const type = data.type
      const msg = this.messages[aiIndex]
      if (!msg || msg.role !== 'assistant') return

      if (type === 'session') {
        const sid = typeof data.content === 'string' ? data.content : ''
        if (sid) {
          this.sessionId = sid
          try {
            localStorage.setItem(SESSION_KEY, sid)
          } catch (e) { /* ignore */ }
        }
        return
      }
      if (type === 'text') {
        const c = data.content || ''
        msg.content = (msg.content || '') + c
        this.$nextTick(this.scrollToBottom)
        return
      }
      if (type === 'thinking') {
        const c = data.content || ''
        msg.thinking = (msg.thinking || '') + c
        return
      }
      if (type === 'recommend') {
        let rec = data.content
        if (typeof rec === 'string') {
          try {
            rec = JSON.parse(rec)
          } catch (e) {
            rec = []
          }
        }
        msg.recommend = Array.isArray(rec) ? rec : []
        return
      }
      if (type === 'error') {
        msg.content = data.content || '服务异常'
        this.$toast.fail(msg.content)
      }
    },
    scrollToBottom() {
      const el = this.$refs.scrollWrap
      if (el) {
        el.scrollTop = el.scrollHeight
      }
    }
  }
}
</script>

<style lang="scss" scoped>
@import "@/styles/variables.scss";

.cs-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f7f8fc 0%, #eef1f8 45%, #f4f5f9 100%);
  display: flex;
  flex-direction: column;
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

.cs-hero {
  position: relative;
  margin: 24px 24px 16px;
  border-radius: 24px;
  overflow: hidden;
  background: linear-gradient(135deg, #1a1d29 0%, #2d3348 50%, #3a3148 100%);
  color: #fff;
  box-shadow: 0 12px 40px rgba(26, 29, 41, 0.35);
}

.cs-hero__glow {
  position: absolute;
  width: 200px;
  height: 200px;
  right: -40px;
  top: -60px;
  background: radial-gradient(circle, rgba(241, 87, 79, 0.55) 0%, transparent 70%);
  pointer-events: none;
}

.cs-hero__inner {
  position: relative;
  padding: 36px 32px 40px;
}

.cs-hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 22px;
  padding: 8px 20px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  margin-bottom: 20px;
}

.cs-hero__title {
  font-size: 40px;
  font-weight: 700;
  margin: 0 0 12px;
  letter-spacing: 1px;
}

.cs-hero__sub {
  margin: 0;
  font-size: 26px;
  opacity: 0.85;
  line-height: 1.5;
}

.cs-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 20px 24px;
  -webkit-overflow-scrolling: touch;
}

.cs-empty {
  padding: 24px 0 8px;
}

.cs-empty__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.cs-chip {
  border: none;
  padding: 16px 24px;
  border-radius: 999px;
  font-size: 26px;
  color: $black;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.cs-row {
  display: flex;
  align-items: flex-end;
  margin-bottom: 28px;
  gap: 12px;
}

.cs-row--user {
  justify-content: flex-end;
}

.cs-row--bot {
  justify-content: flex-start;
}

.cs-avatar {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
}

.cs-avatar--bot {
  background: linear-gradient(145deg, #fff 0%, #f0f2f8 100%);
  color: $theme;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.cs-avatar--user {
  background: linear-gradient(145deg, $theme 0%, #ff8a80 100%);
  color: #fff;
  box-shadow: 0 4px 16px rgba(241, 87, 79, 0.35);
}

.cs-bubble-wrap {
  max-width: 78%;
}

.cs-bubble {
  padding: 22px 26px;
  border-radius: 24px;
  font-size: 28px;
  line-height: 1.55;
  word-break: break-word;
}

.cs-bubble--user {
  background: linear-gradient(135deg, $theme 0%, #ff7a72 100%);
  color: #fff;
  border-bottom-right-radius: 8px;
  box-shadow: 0 8px 24px rgba(241, 87, 79, 0.28);
}

.cs-bubble--bot {
  background: #fff;
  color: $black;
  border-bottom-left-radius: 8px;
  box-shadow: 0 6px 28px rgba(0, 0, 0, 0.07);
}

.cs-bubble--typing {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 120px;
}

.cs-bubble--typing .dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #c5cad6;
  animation: bounce 1.2s infinite ease-in-out;
}
.cs-bubble--typing .dot:nth-child(2) { animation-delay: 0.15s; }
.cs-bubble--typing .dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.65); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.cs-text {
  white-space: pre-wrap;
}

.cs-thinking {
  margin-bottom: 12px;
  border-radius: 16px;
  overflow: hidden;
  background: #f5f7fb;
}

.cs-thinking ::v-deep .van-collapse-item__title {
  font-size: 24px;
  color: $gray-deep;
}

.cs-thinking__pre {
  margin: 0;
  font-size: 22px;
  color: $gray-deep;
  white-space: pre-wrap;
  line-height: 1.45;
}

.cs-rec {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #eef0f5;
}

.cs-rec__label {
  font-size: 22px;
  color: $gray-deep;
  margin-bottom: 12px;
}

.cs-rec__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.cs-rec__tag {
  font-size: 24px;
  padding: 10px 20px;
  border-radius: 999px;
  background: linear-gradient(180deg, #fff5f4 0%, #ffe8e6 100%);
  color: $theme;
  border: 1px solid rgba(241, 87, 79, 0.25);
}

.cs-footer {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding: 12px 20px 20px;
}

.safe-bottom {
  padding-bottom: calc(20px + constant(safe-area-inset-bottom));
  padding-bottom: calc(20px + env(safe-area-inset-bottom));
}

.cs-toolbar {
  min-height: 48px;
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.cs-toolbar__hint {
  font-size: 22px;
  color: $gray-deep;
}

.cs-stop {
  border-radius: 999px !important;
}

.cs-input-bar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  background: #f0f2f7;
  border-radius: 28px;
  padding: 8px 8px 8px 20px;
}

.cs-field {
  flex: 1;
  padding: 8px 0;
  background: transparent;
}

.cs-field ::v-deep .van-field__control {
  font-size: 28px;
  line-height: 1.4;
  max-height: 160px;
}

.cs-send {
  flex-shrink: 0;
  height: 72px !important;
  padding: 0 36px !important;
  font-size: 28px !important;
  box-shadow: 0 6px 20px rgba(241, 87, 79, 0.35);
}
</style>
