import { ref } from 'vue'
import { describeError } from '@/utils/error'

export function useAsync<T>(loader: () => Promise<T>, initial: T) {
  const data = ref<T>(initial) as { value: T }
  const loading = ref(false)
  const error = ref('')

  async function run(): Promise<T | undefined> {
    loading.value = true
    error.value = ''
    try {
      const result = await loader()
      data.value = result
      return result
    } catch (err) {
      error.value = describeError(err)
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, run }
}
