function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return Uint8Array.from([...rawData].map((c) => c.charCodeAt(0)))
}

export type PushSubscribeResult = 'subscribed' | 'unsupported' | 'denied' | 'error'

/**
 * 브라우저 알림 권한을 요청하고 웹 푸시를 구독한다. 브라우저 권한 프롬프트가 막히지 않도록
 * 반드시 사용자 클릭 같은 제스처 안에서 호출해야 한다.
 */
export async function subscribeToPushNotifications(): Promise<PushSubscribeResult> {
  if (typeof window === 'undefined' || !('serviceWorker' in navigator) || !('PushManager' in window)) {
    return 'unsupported'
  }

  try {
    const permission = await Notification.requestPermission()
    if (permission !== 'granted') {
      return 'denied'
    }

    const registration = await navigator.serviceWorker.register('/sw.js')
    await navigator.serviceWorker.ready

    const keyResponse = await fetch('/api/push/vapid-public-key')
    const { publicKey } = (await keyResponse.json()) as { publicKey: string }
    if (!publicKey) {
      return 'error'
    }

    let subscription = await registration.pushManager.getSubscription()
    if (!subscription) {
      subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey) as BufferSource,
      })
    }

    await fetch('/api/push/subscriptions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(subscription.toJSON()),
    })

    return 'subscribed'
  } catch {
    return 'error'
  }
}
