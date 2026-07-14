export function useWebSocket() {
  let ws: WebSocket | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let retryCount = 0
  const MAX_RETRIES = 10
  const BASE_DELAY = 3000

  function connect(url: string, onMessage: (ev: MessageEvent) => void, onOpen?: () => void, onClose?: () => void, onError?: (ev: Event) => void) {
	if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
	ws = new WebSocket(url)
	ws.onopen = () => {
	  retryCount = 0  // 重置重连计数
	  onOpen && onOpen()
	  startHeartbeat()
	}
	ws.onmessage = (ev) => {
	  onMessage(ev)
	}
	ws.onclose = () => {
	  stopHeartbeat()
	  scheduleReconnect(() => connect(url, onMessage, onOpen, onClose, onError))
	  onClose && onClose()
	}
	ws.onerror = (ev) => {
	  onError && onError(ev)
	}
  }

  function disconnect() {
	stopHeartbeat()
	if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
	retryCount = MAX_RETRIES  // 阻止断开后的自动重连
	if (ws) { ws.close(); ws = null }
  }

  function startHeartbeat() {
	stopHeartbeat()
	heartbeatTimer = setInterval(() => {
	  if (ws && ws.readyState === WebSocket.OPEN) {
		try { ws.send(JSON.stringify({ type: 'ping' })) } catch (e) { console.warn('Heartbeat send failed:', e) }
	  }
	}, 30000)
  }

  function stopHeartbeat() {
	if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
  }

  function scheduleReconnect(cb: () => void) {
	if (reconnectTimer || retryCount >= MAX_RETRIES) return
	const delay = Math.min(BASE_DELAY * Math.pow(2, retryCount), 30000)
	retryCount++
	reconnectTimer = setTimeout(() => { reconnectTimer = null; cb() }, delay)
  }

  return { connect, disconnect }
}


