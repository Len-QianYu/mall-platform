import { onBeforeUnmount, ref } from 'vue'

export function useEmailCodeCountdown() {
  const remaining = ref(0)
  let timer: ReturnType<typeof setInterval> | undefined

  const stop = () => {
    if (timer !== undefined) clearInterval(timer)
    timer = undefined
  }

  const start = (seconds = 60) => {
    stop()
    const deadline = Date.now() + seconds * 1000
    remaining.value = Math.ceil(seconds)
    timer = setInterval(() => {
      remaining.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      if (!remaining.value) stop()
    }, 1000)
  }

  onBeforeUnmount(stop)
  return { remaining, start }
}
