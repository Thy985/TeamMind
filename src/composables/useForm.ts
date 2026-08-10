import { ref, computed } from 'vue'
import { z } from 'zod'

/**
 * 表单验证和反馈系统
 */

export interface FieldError {
  field: string
  message: string
  type: 'error' | 'warning' | 'info'
}

export interface FormState {
  values: Record<string, any>
  errors: Record<string, FieldError>
  touched: Record<string, boolean>
  isDirty: boolean
  isSubmitting: boolean
}

/**
 * 表单 Composable
 */
export function useForm<T extends Record<string, any>>(
  initialValues: T,
  schema?: z.ZodSchema<T>,
  options?: {
    onSubmit?: (values: T) => Promise<void>
    onError?: (errors: FieldError[]) => void
  }
) {
  const values = ref<T>({ ...initialValues })
  const errors = ref<Record<string, FieldError>>({})
  const touched = ref<Record<string, boolean>>({})
  const isDirty = ref(false)
  const isSubmitting = ref(false)

  // 计算属性
  const hasErrors = computed(() => Object.keys(errors.value).length > 0)
  const isValid = computed(() => !hasErrors.value)
  const dirtyFields = computed(() =>
    Object.keys(touched.value).filter(key => touched.value[key])
  )

  /**
   * 验证单个字段
   */
  function validateField(field: string, value: any) {
    if (!schema) return

    try {
      // 验证单个字段
      const fieldSchema = (schema as any).shape?.[field]
      if (fieldSchema) {
        fieldSchema.parse(value)
        delete errors.value[field]
      }
    } catch (err: unknown) {
      if (err instanceof z.ZodError) {
        const issue = err.issues[0]
        errors.value[field] = {
          field,
          message: issue.message,
          type: 'error'
        }
      }
    }
  }

  /**
   * 验证所有字段
   */
  function validateAll() {
    if (!schema) return true

    try {
      schema.parse(values.value)
      errors.value = {}
      return true
    } catch (err: unknown) {
      if (err instanceof z.ZodError) {
        errors.value = {}
        err.issues.forEach((issue: any) => {
          const field = String(issue.path[0])
          errors.value[field] = {
            field,
            message: issue.message,
            type: 'error'
          }
        })
      }
      return false
    }
  }

  /**
   * 设置字段值
   */
  function setFieldValue(field: string, value: any) {
    values.value[field] = value
    isDirty.value = true
    validateField(field, value)
  }

  /**
   * 设置字段触碰状态
   */
  function setFieldTouched(field: string, isTouched = true) {
    touched.value[field] = isTouched
  }

  /**
   * 设置字段错误
   */
  function setFieldError(field: string, message: string) {
    errors.value[field] = {
      field,
      message,
      type: 'error'
    }
  }

  /**
   * 清除字段错误
   */
  function clearFieldError(field: string) {
    delete errors.value[field]
  }

  /**
   * 重置表单
   */
  function reset() {
    values.value = { ...initialValues }
    errors.value = {}
    touched.value = {}
    isDirty.value = false
    isSubmitting.value = false
  }

  /**
   * 提交表单
   */
  async function submit() {
    if (!validateAll()) {
      options?.onError?.(Object.values(errors.value))
      return
    }

    isSubmitting.value = true
    try {
      await options?.onSubmit?.(values.value)
    } finally {
      isSubmitting.value = false
    }
  }

  /**
   * 获取字段属性
   */
  function getFieldProps(field: string) {
    return {
      modelValue: values.value[field],
      'onUpdate:modelValue': (value: any) => setFieldValue(field, value),
      onBlur: () => setFieldTouched(field, true),
      error: !!errors.value[field],
      'feedback': errors.value[field]?.message
    }
  }

  return {
    values,
    errors,
    touched,
    isDirty,
    isSubmitting,
    hasErrors,
    isValid,
    dirtyFields,
    validateField,
    validateAll,
    setFieldValue,
    setFieldTouched,
    setFieldError,
    clearFieldError,
    reset,
    submit,
    getFieldProps
  }
}

/**
 * 字段验证规则
 */
export const fieldValidators = {
  required: (message = '此字段为必填项') =>
    z.string().min(1, message),

  email: (message = '请输入有效的邮箱地址') =>
    z.string().email(message),

  minLength: (min: number, message?: string) =>
    z.string().min(min, message || `至少需要 ${min} 个字符`),

  maxLength: (max: number, message?: string) =>
    z.string().max(max, message || `最多只能 ${max} 个字符`),

  pattern: (pattern: RegExp, message = '格式不正确') =>
    z.string().regex(pattern, message),

  number: (message = '请输入数字') =>
    z.number({ invalid_type_error: message }),

  min: (min: number, message?: string) =>
    z.number().min(min, message || `最小值为 ${min}`),

  max: (max: number, message?: string) =>
    z.number().max(max, message || `最大值为 ${max}`)
}
