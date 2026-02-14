<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref, watch } from 'vue'
import { utils } from '@halo-dev/ui-shared'

type RuleOption = {
  code: string
  label: string
}

type RuleConfig = {
  prefix: string
  serialWidth: number
  codePattern: string
  resetPerYear: boolean
  departments: RuleOption[]
  docTypes: RuleOption[]
}

type LedgerItem = {
  id: string
  fullCode: string
  deptCode: string
  docType: string
  status: string
  statusDisplayKey?: string
  statusDisplay?: string
  createdAt: string
  replacesCode?: string
  replacedByCode?: string
  dependencyCodes?: string
  remark?: string
  articleName?: string
  articleTitle?: string
  articleLink?: string
  articlePublishedDate?: string
  effectiveDate?: string
  supersededDate?: string
  voidDate?: string
}

type OperationLog = {
  id: string
  action: string
  operator: string
  targetId?: string
  targetCode?: string
  detail?: string
  changes?: Array<{
    field: string
    fromValue?: string
    toValue?: string
  }>
  createdAt: string
}

type PostOption = {
  name: string
  title: string
  permalink: string
  publishDate: string
}

type Mode = 'query' | 'create' | 'update' | 'manage' | 'logs'
type FilterOption = {
  value: string
  label: string
}

const props = defineProps<{
  mode?: Mode
}>()

const currentMode = computed<Mode>(() => props.mode || 'query')

const modeTitles: Record<Mode, string> = {
  query: '编号查询',
  create: '编号创建',
  update: '编号修改',
  manage: '规则管理',
  logs: '操作日志',
}
const statusText: Record<string, string> = {
  REGISTERED: '已注册',
  BOUND: '现行有效',
  SUPERSEDED: '废止',
  VOID: '废止',
  DELETED: '已删除',
}
const actionText: Record<string, string> = {
  RULE_UPDATED: '规则更新',
  LEDGER_REGISTERED: '编号注册',
  LEDGER_UPDATED: '编号修改',
  LEDGER_MARKED_DELETED: '标记删除',
}
const displayTimeZone = 'Asia/Shanghai'

const baseUrl = '/apis/api.article-id-management.console/v1'
const loading = ref(false)
const savingRule = ref(false)
const previewing = ref(false)
const registering = ref(false)
const updatingLedger = ref(false)
const deletingLedgerId = ref('')
const message = ref('')
const expandedLedgerIds = ref<string[]>([])
const expandedLogIds = ref<string[]>([])
const ledgerPagination = ref({
  page: 1,
  pageSize: 25,
})
const logPagination = ref({
  page: 1,
  pageSize: 20,
})

const rules = ref<RuleConfig>({
  prefix: 'MS',
  serialWidth: 4,
  codePattern: '{前缀}-{部门编码}/{文件类型}-{流水号}{子文件片段}/{年份}{修订片段}',
  resetPerYear: true,
  departments: [],
  docTypes: [],
})

const registerForm = ref({
  deptCode: '',
  docType: '',
  year: new Date().getFullYear(),
  serial: '',
  subSerial: '',
  rev: '',
  replacesCode: [] as string[],
  replacedByCode: [] as string[],
  dependencyCodes: [] as string[],
  remark: '',
  articleName: '',
  articleTitle: '',
  articleLink: '',
  articlePublishedDate: '',
})
const registerPick = ref({
  replacesCode: '',
  replacedByCode: '',
  dependencyCodes: '',
})

const updateForm = ref({
  id: '',
  status: 'REGISTERED',
  replacesCode: [] as string[],
  replacedByCode: [] as string[],
  dependencyCodes: [] as string[],
  remark: '',
  articleName: '',
  articleTitle: '',
  articleLink: '',
  articlePublishedDate: '',
  effectiveDate: '',
  supersededDate: '',
  voidDate: '',
})
const updatePick = ref({
  replacesCode: '',
  replacedByCode: '',
  dependencyCodes: '',
})
const registerPostKeyword = ref('')
const registerPickPostName = ref('')
const updatePostKeyword = ref('')
const updatePickPostName = ref('')

const previewCode = ref('')
const ledger = ref<LedgerItem[]>([])
const logs = ref<OperationLog[]>([])
const postOptions = ref<PostOption[]>([])
const createdSessionLedger = ref<LedgerItem[]>([])
const updateKeyword = ref('')
const ledgerFilter = ref({
  deptCodes: [] as string[],
  docTypes: [] as string[],
  statuses: ['REGISTERED', 'BOUND_PENDING', 'BOUND_EFFECTIVE'] as string[],
  keyword: '',
})
const logFilter = ref({
  actions: [] as string[],
  operators: [] as string[],
  keyword: '',
  start: '',
  end: '',
})
const expandedFilterRows = ref<Record<string, boolean>>({
  ledgerDept: false,
  ledgerType: false,
  ledgerStatus: false,
  logAction: false,
  logOperator: false,
})

const deptOptions = computed(() => rules.value.departments || [])
const typeOptions = computed(() => rules.value.docTypes || [])
const relationOptions = computed(() =>
  ledger.value.map((item) => ({
    code: item.fullCode,
    label: `${item.fullCode}（${statusDisplayLabel(item)}）`,
    id: item.id,
  })),
)
const relationOptionsForUpdate = computed(() =>
  relationOptions.value.filter((item) => item.id !== updateForm.value.id),
)
const deptFilterOptions = computed<FilterOption[]>(() =>
  deptOptions.value.map((item) => ({ value: item.code, label: `${item.code} · ${item.label}` })),
)
const typeFilterOptions = computed<FilterOption[]>(() =>
  typeOptions.value.map((item) => ({ value: item.code, label: `${item.code} · ${item.label}` })),
)
const statusFilterOptions: FilterOption[] = [
  { value: 'REGISTERED', label: '已注册' },
  { value: 'BOUND_PENDING', label: '即将生效' },
  { value: 'BOUND_EFFECTIVE', label: '现行有效' },
  { value: 'TERMINATED', label: '废止' },
  { value: 'DELETED', label: '已删除' },
]
const actionFilterOptions = computed<FilterOption[]>(() =>
  logActions.value.map((item) => ({ value: item, label: actionLabel(item) })),
)
const operatorFilterOptions = computed<FilterOption[]>(() =>
  logOperators.value.map((item) => ({ value: item, label: item })),
)
const filteredLedger = computed(() => {
  const deptCodes = ledgerFilter.value.deptCodes
  const docTypes = ledgerFilter.value.docTypes
  const statuses = ledgerFilter.value.statuses
  const keyword = ledgerFilter.value.keyword.trim().toLowerCase()

  return ledger.value.filter((item) => {
    if (deptCodes.length > 0 && !deptCodes.includes(item.deptCode)) {
      return false
    }
    if (docTypes.length > 0 && !docTypes.includes(item.docType)) {
      return false
    }
    if (statuses.length > 0 && !statuses.includes(statusFilterKey(item))) {
      return false
    }
    if (keyword && !item.fullCode.toLowerCase().includes(keyword)) {
      return false
    }
    return true
  })
})
const filteredUpdateLedger = computed(() => {
  const keyword = updateKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return ledger.value
  }
  return ledger.value.filter((item) => {
    const text = [item.fullCode, item.deptCode, item.docType, statusDisplayLabel(item)].join(' ').toLowerCase()
    return text.includes(keyword)
  })
})
const selectedUpdateItem = computed(() => ledger.value.find((item) => item.id === updateForm.value.id) || null)
const filteredRegisterPostOptions = computed(() => {
  const available = availableRegisterPostOptions.value
  const keyword = registerPostKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return available
  }
  return available.filter((item) => `${item.title} ${item.permalink}`.toLowerCase().includes(keyword))
})
const filteredUpdatePostOptions = computed(() => {
  const available = availableUpdatePostOptions.value
  const keyword = updatePostKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return available
  }
  return available.filter((item) => `${item.title} ${item.permalink}`.toLowerCase().includes(keyword))
})
const occupiedBindingNames = computed(() => {
  const names = new Set<string>()
  ledger.value.forEach((item) => {
    if (item.articleName) {
      names.add(item.articleName)
    }
  })
  return names
})
const availableRegisterPostOptions = computed(() =>
  postOptions.value.filter((item) => !occupiedBindingNames.value.has(item.name)),
)
const availableUpdatePostOptions = computed(() =>
  postOptions.value.filter((item) => item.name === updateForm.value.articleName || !occupiedBindingNames.value.has(item.name)),
)

const canView = computed(() => hasPermission(['plugin:article-id-management:view', 'plugin:article-id-management:create', 'plugin:article-id-management:modify', 'plugin:article-id-management:manage']))
const canCreate = computed(() => hasPermission(['plugin:article-id-management:create', 'plugin:article-id-management:manage']))
const canModify = computed(() => hasPermission(['plugin:article-id-management:modify', 'plugin:article-id-management:manage']))
const canManage = computed(() => hasPermission(['plugin:article-id-management:manage']))
const logActions = computed(() => Array.from(new Set(logs.value.map((item) => item.action).filter(Boolean))))
const logOperators = computed(() => Array.from(new Set(logs.value.map((item) => item.operator).filter(Boolean))))
const filteredLogs = computed(() => {
  const actions = logFilter.value.actions
  const operators = logFilter.value.operators
  const keyword = logFilter.value.keyword.trim().toLowerCase()
  const startAt = logFilter.value.start ? new Date(`${logFilter.value.start}T00:00:00`).getTime() : null
  const endAt = logFilter.value.end ? new Date(`${logFilter.value.end}T23:59:59`).getTime() : null

  return logs.value.filter((item) => {
    if (actions.length > 0 && !actions.includes(item.action)) {
      return false
    }
    if (operators.length > 0 && !operators.includes(item.operator || '')) {
      return false
    }
    if (keyword && !(item.targetCode || '').toLowerCase().includes(keyword)) {
      return false
    }
    if (startAt || endAt) {
      const current = new Date(item.createdAt).getTime()
      if (startAt && current < startAt) {
        return false
      }
      if (endAt && current > endAt) {
        return false
      }
    }
    return true
  })
})
const ledgerPageSizes = [10, 20, 25, 50, 100]
const logPageSizes = [10, 20, 25, 50]
const ledgerTotalPages = computed(() =>
  Math.max(1, Math.ceil(filteredLedger.value.length / ledgerPagination.value.pageSize)),
)
const logTotalPages = computed(() => Math.max(1, Math.ceil(filteredLogs.value.length / logPagination.value.pageSize)))
const pagedFilteredLedger = computed(() => {
  const start = (ledgerPagination.value.page - 1) * ledgerPagination.value.pageSize
  return filteredLedger.value.slice(start, start + ledgerPagination.value.pageSize)
})
const pagedFilteredLogs = computed(() => {
  const start = (logPagination.value.page - 1) * logPagination.value.pageSize
  return filteredLogs.value.slice(start, start + logPagination.value.pageSize)
})

function hasPermission(permissions: string[]) {
  try {
    return utils.permission.has(permissions, true)
  } catch {
    return true
  }
}

function normalizeOptions(items: Array<{ code?: string; label?: string; name?: string }>) {
  return (items || []).map((item) => ({
    code: item.code || '',
    label: item.label || item.name || '',
  }))
}

function normalizeLedgerItem(item: any): LedgerItem | null {
  if (!item || typeof item !== 'object' || !item.id || !item.fullCode) {
    return null
  }
  return {
    id: String(item.id),
    fullCode: String(item.fullCode),
    deptCode: String(item.deptCode || ''),
    docType: String(item.docType || ''),
    status: String(item.status || 'REGISTERED'),
    statusDisplayKey: item.statusDisplayKey ? String(item.statusDisplayKey) : '',
    statusDisplay: item.statusDisplay ? String(item.statusDisplay) : '',
    createdAt: String(item.createdAt || ''),
    replacesCode: item.replacesCode ? String(item.replacesCode) : '',
    replacedByCode: item.replacedByCode ? String(item.replacedByCode) : '',
    dependencyCodes: item.dependencyCodes ? String(item.dependencyCodes) : '',
    remark: item.remark ? String(item.remark) : '',
    articleName: item.articleName ? String(item.articleName) : '',
    articleTitle: item.articleTitle ? String(item.articleTitle) : '',
    articleLink: item.articleLink ? String(item.articleLink) : '',
    articlePublishedDate: item.articlePublishedDate ? String(item.articlePublishedDate) : '',
    effectiveDate: item.effectiveDate ? String(item.effectiveDate) : '',
    supersededDate: item.supersededDate ? String(item.supersededDate) : '',
    voidDate: item.voidDate ? String(item.voidDate) : '',
  }
}

function normalizePostOption(item: any): PostOption | null {
  const name = item?.metadata?.name
  if (!name) {
    return null
  }
  return {
    name: String(name),
    title: String(item?.spec?.title || item?.metadata?.name || ''),
    permalink: String(item?.status?.permalink || ''),
    publishDate: String(item?.spec?.publishTime || ''),
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    const [ruleResp, ledgerResp] = await Promise.all([
      axios.get(`${baseUrl}/rules`),
      axios.get(`${baseUrl}/ledger`),
    ])
    rules.value = {
      codePattern: '{前缀}-{部门编码}/{文件类型}-{流水号}{子文件片段}/{年份}{修订片段}',
      ...ruleResp.data,
      departments: normalizeOptions(ruleResp.data.departments),
      docTypes: normalizeOptions(ruleResp.data.docTypes),
    }
    ledger.value = Array.isArray(ledgerResp.data)
      ? ledgerResp.data.map((item: any) => normalizeLedgerItem(item)).filter(Boolean) as LedgerItem[]
      : []
    try {
      const postResp = await axios.get('/apis/api.content.halo.run/v1alpha1/posts?page=1&size=200')
      postOptions.value = Array.isArray(postResp?.data?.items)
        ? postResp.data.items.map((item: any) => normalizePostOption(item)).filter(Boolean) as PostOption[]
        : []
    } catch {
      // Some roles can use this plugin without content-module permissions.
      postOptions.value = []
    }
    try {
      const logResp = await axios.get(`${baseUrl}/logs`)
      logs.value = logResp.data
    } catch {
      logs.value = []
    }

    if (!registerForm.value.deptCode && deptOptions.value.length > 0) {
      registerForm.value.deptCode = deptOptions.value[0].code
    }
    if (!registerForm.value.docType && typeOptions.value.length > 0) {
      registerForm.value.docType = typeOptions.value[0].code
    }
  } catch (err: any) {
    message.value = err?.response?.data?.message || '数据加载失败'
  } finally {
    loading.value = false
  }
}

const addOption = (target: 'departments' | 'docTypes') => {
  rules.value[target].push({ code: '', label: '' })
}

const removeOption = (target: 'departments' | 'docTypes', idx: number) => {
  rules.value[target].splice(idx, 1)
}

const saveRules = async () => {
  savingRule.value = true
  message.value = ''
  try {
    const payload = {
      ...rules.value,
      departments: rules.value.departments.filter((item) => item.code && item.label),
      docTypes: rules.value.docTypes.filter((item) => item.code && item.label),
    }
    const resp = await axios.put(`${baseUrl}/rules`, payload)
    rules.value = {
      ...resp.data,
      departments: normalizeOptions(resp.data.departments),
      docTypes: normalizeOptions(resp.data.docTypes),
    }
    await loadAll()
    message.value = '规则配置已保存'
  } catch (err: any) {
    message.value = err?.response?.data?.message || '规则配置保存失败'
  } finally {
    savingRule.value = false
  }
}

const buildPayload = () => ({
  deptCode: registerForm.value.deptCode,
  docType: registerForm.value.docType,
  year: Number(registerForm.value.year),
  serial: registerForm.value.serial ? Number(registerForm.value.serial) : null,
  subSerial: registerForm.value.subSerial ? Number(registerForm.value.subSerial) : null,
  rev: registerForm.value.rev ? Number(registerForm.value.rev) : null,
  replacesCode: joinCodes(registerForm.value.replacesCode),
  replacedByCode: joinCodes(registerForm.value.replacedByCode),
  dependencyCodes: joinCodes(registerForm.value.dependencyCodes),
  remark: registerForm.value.remark || null,
  articleName: registerForm.value.articleName || null,
  articleTitle: registerForm.value.articleTitle || null,
  articleLink: registerForm.value.articleLink || null,
  articlePublishedDate: registerForm.value.articlePublishedDate || null,
})

const isLikelyUrl = (value: string) => {
  const input = value.trim()
  return /^(https?:\/\/|\/)/i.test(input)
}

const toRelativePath = (input: string) => {
  const normalized = input.trim()
  const parsed = new URL(normalized, window.location.origin)
  if (parsed.origin !== window.location.origin) {
    throw new Error('仅支持本站 URL')
  }
  return `${parsed.pathname}${parsed.search}${parsed.hash}`
}

const resolveBindingFromUrl = async (input: string): Promise<PostOption | null> => {
  try {
    const relativePath = toRelativePath(input)
    const resp = await fetch(relativePath, { method: 'GET', credentials: 'include' })
    if (!resp.ok) {
      return null
    }
    const html = await resp.text()
    const doc = new DOMParser().parseFromString(html, 'text/html')
    const titleText = (doc.querySelector('title')?.textContent || '').trim()
    const fallbackTitle = relativePath.split('/').pop() || relativePath
    return {
      name: relativePath,
      title: titleText || fallbackTitle,
      permalink: relativePath,
      publishDate: '',
    }
  } catch {
    return null
  }
}

const applyPostToRegister = async (selectedName?: string) => {
  const pickName = selectedName || registerPickPostName.value
  let target = pickName ? postOptions.value.find((item) => item.name === pickName) : undefined
  if (!target && !pickName && isLikelyUrl(registerPostKeyword.value)) {
    const urlTarget = await resolveBindingFromUrl(registerPostKeyword.value)
    if (urlTarget) {
      target = urlTarget
      registerPickPostName.value = ''
    }
  }
  if (!target) {
    message.value = pickName ? '未找到对应绑定对象' : '请选择对象，或输入本站 URL 后点击选择'
    return
  }
  registerPickPostName.value = target.name
  registerForm.value.articleName = target.name
  registerForm.value.articleTitle = target.title
  registerForm.value.articleLink = target.permalink || ''
  registerForm.value.articlePublishedDate = target.publishDate ? target.publishDate.slice(0, 10) : ''
}

const applyPostToUpdate = async (selectedName?: string) => {
  const pickName = selectedName || updatePickPostName.value
  let target = pickName ? postOptions.value.find((item) => item.name === pickName) : undefined
  if (!target && !pickName && isLikelyUrl(updatePostKeyword.value)) {
    const urlTarget = await resolveBindingFromUrl(updatePostKeyword.value)
    if (urlTarget) {
      target = urlTarget
      updatePickPostName.value = ''
    }
  }
  if (!target) {
    message.value = pickName ? '未找到对应绑定对象' : '请选择对象，或输入本站 URL 后点击选择'
    return
  }
  updatePickPostName.value = target.name
  updateForm.value.articleName = target.name
  updateForm.value.articleTitle = target.title
  updateForm.value.articleLink = target.permalink || ''
  updateForm.value.articlePublishedDate = target.publishDate ? target.publishDate.slice(0, 10) : ''
}
const onRegisterPostSelectChange = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  void applyPostToRegister(value)
}
const onUpdatePostSelectChange = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  void applyPostToUpdate(value)
}

const preview = async () => {
  previewing.value = true
  message.value = ''
  try {
    const resp = await axios.post(`${baseUrl}/ledger/preview`, buildPayload())
    previewCode.value = resp.data.fullCode
  } catch (err: any) {
    message.value = err?.response?.data?.message || '编号预览失败'
    previewCode.value = ''
  } finally {
    previewing.value = false
  }
}

const registerCode = async () => {
  registering.value = true
  message.value = ''
  try {
    const resp = await axios.post(`${baseUrl}/ledger/register`, buildPayload())
    const created = normalizeLedgerItem(resp.data)
    if (created) {
      createdSessionLedger.value = [created, ...createdSessionLedger.value.filter((item) => item.id !== created.id)]
    }
    message.value = '编号注册成功'
    previewCode.value = ''
    registerForm.value.serial = ''
    registerForm.value.subSerial = ''
    registerForm.value.rev = ''
    registerForm.value.replacesCode = []
    registerForm.value.replacedByCode = []
    registerForm.value.dependencyCodes = []
    registerPick.value.replacesCode = ''
    registerPick.value.replacedByCode = ''
    registerPick.value.dependencyCodes = ''
    registerForm.value.remark = ''
    registerForm.value.articleName = ''
    registerForm.value.articleTitle = ''
    registerForm.value.articleLink = ''
    registerForm.value.articlePublishedDate = ''
    registerPickPostName.value = ''
    await loadAll()
  } catch (err: any) {
    message.value = err?.response?.data?.message || '编号注册失败'
  } finally {
    registering.value = false
  }
}

const pickUpdateTarget = (item: LedgerItem) => {
  updateForm.value.id = item.id
  updateForm.value.status = item.status
  updateForm.value.replacesCode = splitCodes(item.replacesCode)
  updateForm.value.replacedByCode = splitCodes(item.replacedByCode)
  updateForm.value.dependencyCodes = splitCodes(item.dependencyCodes)
  updateForm.value.remark = item.remark || ''
  updateForm.value.articleTitle = item.articleTitle || ''
  updateForm.value.articleName = item.articleName || ''
  updateForm.value.articleLink = item.articleLink || ''
  updateForm.value.articlePublishedDate = item.articlePublishedDate || ''
  updateForm.value.effectiveDate = item.effectiveDate || ''
  updateForm.value.supersededDate = item.supersededDate || ''
  updateForm.value.voidDate = item.voidDate || ''
  updatePickPostName.value = item.articleName || ''
}

const updateLedger = async () => {
  if (!updateForm.value.id) {
    message.value = '请先从台账中选择一条记录'
    return
  }
  updatingLedger.value = true
  message.value = ''
  try {
    let autoRefreshBinding = false
    const bindName = (updateForm.value.articleName || '').trim()
    const bindLink = (updateForm.value.articleLink || '').trim()
    const refreshSource = bindName && isLikelyUrl(bindName)
      ? bindName
      : (bindLink && isLikelyUrl(bindLink) ? bindLink : '')
    if (refreshSource) {
      const latest = await resolveBindingFromUrl(refreshSource)
      if (latest) {
        updateForm.value.articleTitle = latest.title || updateForm.value.articleTitle
        updateForm.value.articleLink = latest.permalink || updateForm.value.articleLink
        updateForm.value.articlePublishedDate = latest.publishDate
          ? latest.publishDate.slice(0, 10)
          : updateForm.value.articlePublishedDate
        autoRefreshBinding = true
      }
    }
    const currentItem = selectedUpdateItem.value
    const payload: Record<string, unknown> = {
      // 更新场景下需要显式传空字符串，确保“清空关系字段”会被后端持久化。
      replacesCode: joinCodes(updateForm.value.replacesCode) ?? '',
      replacedByCode: joinCodes(updateForm.value.replacedByCode) ?? '',
      dependencyCodes: joinCodes(updateForm.value.dependencyCodes) ?? '',
      remark: updateForm.value.remark,
      articleName: updateForm.value.articleName || null,
      articleTitle: updateForm.value.articleTitle || null,
      articleLink: updateForm.value.articleLink || null,
      articlePublishedDate: updateForm.value.articlePublishedDate || null,
      autoRefreshBinding,
      effectiveDate: updateForm.value.effectiveDate || null,
      supersededDate: updateForm.value.supersededDate || null,
      voidDate: updateForm.value.voidDate || null,
    }
    if (updateForm.value.status && updateForm.value.status !== (currentItem?.status || '')) {
      payload.status = updateForm.value.status
    }
    const resp = await axios.patch(`${baseUrl}/ledger/${updateForm.value.id}`, payload)
    message.value = '编号更新成功'
    await loadAll()
    const saved = normalizeLedgerItem(resp.data)
    const savedId = saved?.id || updateForm.value.id
    const latest = ledger.value.find((item) => item.id === savedId)
    if (latest) {
      pickUpdateTarget(latest)
    }
  } catch (err: any) {
    message.value = err?.response?.data?.message || '编号更新失败'
  } finally {
    updatingLedger.value = false
  }
}

const markDeleted = async (item: LedgerItem) => {
  deletingLedgerId.value = item.id
  message.value = ''
  try {
    await axios.post(`${baseUrl}/ledger/${item.id}/mark-delete`)
    message.value = '已标记删除'
    if (updateForm.value.id === item.id) {
      updateForm.value.status = 'DELETED'
    }
    await loadAll()
  } catch (err: any) {
    message.value = err?.response?.data?.message || '标记删除失败'
  } finally {
    deletingLedgerId.value = ''
  }
}

const statusLabel = (status: string) => statusText[status] || status
const statusFilterKey = (item: LedgerItem) => {
  if (item.statusDisplayKey) {
    return item.statusDisplayKey
  }
  if (item.status === 'SUPERSEDED' || item.status === 'VOID') {
    return 'TERMINATED'
  }
  if (item.status === 'BOUND') {
    return 'BOUND_EFFECTIVE'
  }
  return item.status
}
const statusDisplayLabel = (item: LedgerItem) => item.statusDisplay || statusLabel(item.status)
const actionLabel = (action: string) => actionText[action] || action
const formatDateTime = (value?: string) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: displayTimeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}
const addRelation = (target: string[], code: string) => {
  const normalized = (code || '').trim()
  if (!normalized) return
  if (!target.includes(normalized)) {
    target.push(normalized)
  }
}
const removeRelation = (target: string[], code: string) => {
  const idx = target.indexOf(code)
  if (idx >= 0) {
    target.splice(idx, 1)
  }
}
const splitCodes = (text?: string) =>
  (text || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
const hasRelation = (text?: string) => splitCodes(text).length > 0
const relationText = (text?: string) => {
  const codes = splitCodes(text)
  return codes.length > 0 ? codes.join('，') : '-'
}
const joinCodes = (arr?: string[]) => {
  const values = (arr || []).map((item) => item.trim()).filter(Boolean)
  return values.length > 0 ? values.join(',') : null
}

const resetLogFilter = () => {
  logFilter.value = {
    actions: [],
    operators: [],
    keyword: '',
    start: '',
    end: '',
  }
  logPagination.value.page = 1
}
const toggleLogDetail = (id: string) => {
  const idx = expandedLogIds.value.indexOf(id)
  if (idx >= 0) {
    expandedLogIds.value.splice(idx, 1)
  } else {
    expandedLogIds.value.push(id)
  }
}
const isLogExpanded = (id: string) => expandedLogIds.value.includes(id)
const toggleLedgerDetail = (id: string) => {
  const idx = expandedLedgerIds.value.indexOf(id)
  if (idx >= 0) {
    expandedLedgerIds.value.splice(idx, 1)
  } else {
    expandedLedgerIds.value.push(id)
  }
}
const isLedgerExpanded = (id: string) => expandedLedgerIds.value.includes(id)
const resetLedgerFilter = () => {
  ledgerFilter.value = {
    deptCodes: [],
    docTypes: [],
    statuses: ['REGISTERED', 'BOUND_PENDING', 'BOUND_EFFECTIVE'],
    keyword: '',
  }
  ledgerPagination.value.page = 1
}

const toggleFilterValue = (target: string[], value: string) => {
  const idx = target.indexOf(value)
  if (idx >= 0) {
    target.splice(idx, 1)
  } else {
    target.push(value)
  }
}
const isFilterValueSelected = (target: string[], value: string) => target.includes(value)
const getVisibleFilterOptions = (_key: string, options: FilterOption[]) => options
const getHiddenFilterCount = (key: string, options: FilterOption[]) =>
  Math.max(0, options.length - getVisibleFilterOptions(key, options).length)
const toggleFilterRowExpand = (key: string) => {
  expandedFilterRows.value[key] = !expandedFilterRows.value[key]
}
const changeLedgerPage = (nextPage: number) => {
  ledgerPagination.value.page = Math.min(Math.max(nextPage, 1), ledgerTotalPages.value)
}
const changeLogPage = (nextPage: number) => {
  logPagination.value.page = Math.min(Math.max(nextPage, 1), logTotalPages.value)
}
const changeLedgerPageSize = (pageSize: number) => {
  ledgerPagination.value.pageSize = pageSize
  ledgerPagination.value.page = 1
}
const changeLogPageSize = (pageSize: number) => {
  logPagination.value.pageSize = pageSize
  logPagination.value.page = 1
}
const onLedgerPageSizeChange = (event: Event) => {
  const value = Number((event.target as HTMLSelectElement).value)
  if (!Number.isNaN(value)) {
    changeLedgerPageSize(value)
  }
}
const onLogPageSizeChange = (event: Event) => {
  const value = Number((event.target as HTMLSelectElement).value)
  if (!Number.isNaN(value)) {
    changeLogPageSize(value)
  }
}

watch(currentMode, () => {
  message.value = ''
  expandedLedgerIds.value = []
  expandedLogIds.value = []
})
watch(
  () => [filteredLedger.value.length, ledgerPagination.value.pageSize],
  () => {
    if (ledgerPagination.value.page > ledgerTotalPages.value) {
      ledgerPagination.value.page = ledgerTotalPages.value
    }
  },
)
watch(
  () => [filteredLogs.value.length, logPagination.value.pageSize],
  () => {
    if (logPagination.value.page > logTotalPages.value) {
      logPagination.value.page = logTotalPages.value
    }
  },
)

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <h1>文章编号管理</h1>
      
    </header>

    <p v-if="message" class="message">{{ message }}</p>

    <section v-if="!canView && !canCreate && !canModify && !canManage" class="card">
      <h2>无权限访问</h2>
      <p>当前账号没有文章编号插件权限，请联系管理员分配。</p>
    </section>

    <section v-if="currentMode === 'manage' && canManage" class="card">
      <h2>规则配置</h2>
      <div class="grid compact">
        <label>
          前缀
          <input v-model="rules.prefix" class="input-editable" type="text" />
        </label>
        <label>
          流水号位数
          <input v-model.number="rules.serialWidth" class="input-editable" type="number" min="2" max="8" />
        </label>
        <label class="checkbox">
          <input v-model="rules.resetPerYear" type="checkbox" />
          按年份重置流水号
        </label>
      </div>
      <label>
        编号显示规则
        <input
          v-model="rules.codePattern"
          class="input-editable mono"
          type="text"
          placeholder="{前缀}-{部门编码}/{文件类型}-{流水号}{子文件片段}/{年份}{修订片段}"
        />
      </label>
      <p class="hint">
        支持占位符：
        {前缀} {部门编码} {文件类型} {流水号} {子文件号} {年份} {修订号} {子文件片段} {修订片段}
      </p>

      <div class="option-editor-wrap">
        <div class="option-editor">
          <div class="option-header">
            <h3>部门编码</h3>
            <button @click="addOption('departments')">新增</button>
          </div>
          <div class="option-list">
            <div v-for="(item, idx) in rules.departments" :key="`dep-${idx}`" class="option-row">
              <input v-model="item.code" placeholder="code" />
              <input v-model="item.label" placeholder="label" />
              <button class="danger" @click="removeOption('departments', idx)">删除</button>
            </div>
          </div>
        </div>

        <div class="option-editor">
          <div class="option-header">
            <h3>文件类型</h3>
            <button @click="addOption('docTypes')">新增</button>
          </div>
          <div class="option-list">
            <div v-for="(item, idx) in rules.docTypes" :key="`type-${idx}`" class="option-row">
              <input v-model="item.code" placeholder="code" />
              <input v-model="item.label" placeholder="label" />
              <button class="danger" @click="removeOption('docTypes', idx)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <button :disabled="savingRule" @click="saveRules">{{ savingRule ? '保存中...' : '保存规则' }}</button>
    </section>

    <section v-if="currentMode === 'create' && canCreate" class="card">
      <h2>注册编号</h2>
      <div class="grid">
        <label>
          部门
          <select v-model="registerForm.deptCode">
            <option v-for="item in deptOptions" :key="item.code" :value="item.code">
              {{ item.code }} · {{ item.label }}
            </option>
          </select>
        </label>
        <label>
          文件类型
          <select v-model="registerForm.docType">
            <option v-for="item in typeOptions" :key="item.code" :value="item.code">
              {{ item.code }} · {{ item.label }}
            </option>
          </select>
        </label>
        <label>
          年份
          <input v-model.number="registerForm.year" type="number" min="2000" max="2100" />
        </label>
        <label>
          流水号（可空=自动）
          <input v-model="registerForm.serial" type="number" min="1" />
        </label>
        <label>
          子文件号（可空）
          <input v-model="registerForm.subSerial" type="number" min="1" />
        </label>
        <label>
          Rev（可空）
          <input v-model="registerForm.rev" type="number" min="1" />
        </label>
        <label class="field-full">
          绑定文章（选择器）
          <p class="hint">已绑定对象不会重复出现在列表中；若无匹配，可输入本站 URL 后点击选择。</p>
          <div class="post-picker">
            <input v-model="registerPostKeyword" type="text" placeholder="搜索文章标题或链接" />
            <select v-model="registerPickPostName" @change="onRegisterPostSelectChange">
              <option value="">请选择文章</option>
              <option v-for="item in filteredRegisterPostOptions" :key="`register-post-${item.name}`" :value="item.name">
                {{ item.title }} {{ item.permalink ? `(${item.permalink})` : '' }}
              </option>
            </select>
            <button type="button" class="small" @click="() => void applyPostToRegister()">选择</button>
          </div>
        </label>
        <label>
          替代（来源编号）
          <div class="relation-picker">
            <select v-model="registerPick.replacesCode">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptions" :key="`rep-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(registerForm.replacesCode, registerPick.replacesCode)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in registerForm.replacesCode" :key="`c-rep-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(registerForm.replacesCode, code)">×</button>
            </span>
          </div>
        </label>
        <label>
          被替代（去向编号）
          <div class="relation-picker">
            <select v-model="registerPick.replacedByCode">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptions" :key="`repby-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(registerForm.replacedByCode, registerPick.replacedByCode)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in registerForm.replacedByCode" :key="`c-repby-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(registerForm.replacedByCode, code)">×</button>
            </span>
          </div>
        </label>
        <label>
          依赖
          <div class="relation-picker">
            <select v-model="registerPick.dependencyCodes">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptions" :key="`dep-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(registerForm.dependencyCodes, registerPick.dependencyCodes)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in registerForm.dependencyCodes" :key="`c-dep-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(registerForm.dependencyCodes, code)">×</button>
            </span>
          </div>
        </label>
      </div>
      <label>
        备注
        <input v-model="registerForm.remark" type="text" />
      </label>
      <div class="actions">
        <button :disabled="previewing" @click="preview">{{ previewing ? '预览中...' : '预览编号' }}</button>
        <button :disabled="registering" @click="registerCode">{{ registering ? '提交中...' : '注册编号' }}</button>
      </div>
      <p v-if="previewCode" class="preview">预览结果：{{ previewCode }}</p>
    </section>

    <section v-if="currentMode === 'create' && canCreate" class="card">
      <h2>本次创建编号</h2>
      <p class="hint">仅显示当前页面会话中新注册的编号，刷新页面后自动清空。</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>部门</th>
              <th>类型</th>
              <th>状态</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in createdSessionLedger" :key="`created-${item.id}`">
              <td>{{ item.fullCode }}</td>
              <td>{{ item.deptCode }}</td>
              <td>{{ item.docType }}</td>
              <td>{{ statusDisplayLabel(item) }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
            </tr>
            <tr v-if="createdSessionLedger.length === 0">
              <td colspan="5">暂无本次创建记录</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="currentMode === 'update' && canModify" class="card">
      <h2>编号修改</h2>
      <div class="grid compact">
        <label>
          快速检索
          <input v-model="updateKeyword" type="text" placeholder="按编号 / 部门 / 类型 / 状态检索" />
        </label>
      </div>
      <div v-if="updateForm.id" class="update-detail-panel">
        <h3>详细编辑：{{ selectedUpdateItem?.fullCode || updateForm.id }}</h3>
        <div class="grid compact">
          <label>
            记录 ID
            <input v-model="updateForm.id" type="text" readonly />
          </label>
          <label>
            状态
            <select v-model="updateForm.status">
              <option value="REGISTERED">已注册</option>
              <option value="BOUND">已绑定（按生效日期动态展示）</option>
              <option value="SUPERSEDED">废止（替代）</option>
              <option value="VOID">废止（作废）</option>
              <option value="DELETED">已删除</option>
            </select>
          </label>
        </div>
        <label>
          备注
          <input v-model="updateForm.remark" type="text" />
        </label>
        <div class="grid compact">
          <label>
            绑定文章（选择器）
            <p class="hint">已绑定对象不会重复出现在列表中；若无匹配，可输入本站 URL 后点击选择。</p>
            <div class="post-picker">
              <input v-model="updatePostKeyword" type="text" placeholder="搜索文章标题或链接" />
              <select v-model="updatePickPostName" @change="onUpdatePostSelectChange">
                <option value="">请选择文章</option>
                <option v-for="item in filteredUpdatePostOptions" :key="`update-post-${item.name}`" :value="item.name">
                  {{ item.title }} {{ item.permalink ? `(${item.permalink})` : '' }}
                </option>
              </select>
              <button type="button" class="small" @click="() => void applyPostToUpdate()">选择</button>
            </div>
          </label>
          <label class="binding-info">
            绑定文章标题
            <span class="value-label">{{ updateForm.articleTitle || '-' }}</span>
            <span class="binding-sub-label">绑定文章链接</span>
            <span class="value-label mono">{{ updateForm.articleLink || '-' }}</span>
          </label>
          <label>
            绑定文章发布日期
            <input v-model="updateForm.articlePublishedDate" type="date" />
          </label>
          <label>
            生效日期
            <input v-model="updateForm.effectiveDate" type="date" />
          </label>
          <label>
            替代日期
            <input v-model="updateForm.supersededDate" type="date" />
          </label>
          <label>
            作废日期
            <input v-model="updateForm.voidDate" type="date" />
          </label>
        </div>
        <label>
          替代（来源编号）
          <div class="relation-picker">
            <select v-model="updatePick.replacesCode">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptionsForUpdate" :key="`u-rep-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(updateForm.replacesCode, updatePick.replacesCode)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in updateForm.replacesCode" :key="`u-c-rep-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(updateForm.replacesCode, code)">×</button>
            </span>
          </div>
        </label>
        <label>
          被替代（去向编号）
          <div class="relation-picker">
            <select v-model="updatePick.replacedByCode">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptionsForUpdate" :key="`u-repby-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(updateForm.replacedByCode, updatePick.replacedByCode)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in updateForm.replacedByCode" :key="`u-c-repby-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(updateForm.replacedByCode, code)">×</button>
            </span>
          </div>
        </label>
        <label>
          依赖
          <div class="relation-picker">
            <select v-model="updatePick.dependencyCodes">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptionsForUpdate" :key="`u-dep-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(updateForm.dependencyCodes, updatePick.dependencyCodes)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in updateForm.dependencyCodes" :key="`u-c-dep-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(updateForm.dependencyCodes, code)">×</button>
            </span>
          </div>
        </label>
        <div class="actions">
          <button :disabled="updatingLedger" @click="updateLedger">{{ updatingLedger ? '更新中...' : '保存修改' }}</button>
          <button
            type="button"
            class="danger"
            :disabled="!selectedUpdateItem || selectedUpdateItem.status === 'DELETED' || deletingLedgerId === selectedUpdateItem.id"
            @click="selectedUpdateItem && markDeleted(selectedUpdateItem)"
          >
            {{ deletingLedgerId === selectedUpdateItem?.id ? '处理中...' : '标记删除' }}
          </button>
        </div>
      </div>
      <p v-else class="hint">点击下方条目后进入详细编辑。</p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>部门</th>
              <th>类型</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in filteredUpdateLedger"
              :key="`update-ledger-${item.id}`"
              class="simple-table-row"
              :class="{ active: updateForm.id === item.id }"
              @click="pickUpdateTarget(item)"
            >
              <td>{{ item.fullCode }}</td>
              <td>{{ item.deptCode }}</td>
              <td>{{ item.docType }}</td>
              <td>{{ statusDisplayLabel(item) }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
              <td><button type="button" class="small">编辑</button></td>
            </tr>
            <tr v-if="filteredUpdateLedger.length === 0">
              <td colspan="6">暂无可修改条目</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="currentMode === 'query' && (canView || canCreate || canModify || canManage)" class="card">
      <h2>编号台账</h2>
      <div class="filter-panel ledger-filter">
        <div class="filter-row">
          <div class="filter-title">部门</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.ledgerDept }">
            <button
              v-for="item in getVisibleFilterOptions('ledgerDept', deptFilterOptions)"
              :key="`filter-dept-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(ledgerFilter.deptCodes, item.value) }"
              @click="toggleFilterValue(ledgerFilter.deptCodes, item.value)"
            >
              {{ item.label }}
            </button>
            <button
              v-if="getHiddenFilterCount('ledgerDept', deptFilterOptions) > 0"
              type="button"
              class="filter-pill expand"
              @click="toggleFilterRowExpand('ledgerDept')"
            >
              {{ expandedFilterRows.ledgerDept ? '收起' : `+${getHiddenFilterCount('ledgerDept', deptFilterOptions)}` }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <div class="filter-title">文件类型</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.ledgerType }">
            <button
              v-for="item in getVisibleFilterOptions('ledgerType', typeFilterOptions)"
              :key="`filter-type-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(ledgerFilter.docTypes, item.value) }"
              @click="toggleFilterValue(ledgerFilter.docTypes, item.value)"
            >
              {{ item.label }}
            </button>
            <button
              v-if="getHiddenFilterCount('ledgerType', typeFilterOptions) > 0"
              type="button"
              class="filter-pill expand"
              @click="toggleFilterRowExpand('ledgerType')"
            >
              {{ expandedFilterRows.ledgerType ? '收起' : `+${getHiddenFilterCount('ledgerType', typeFilterOptions)}` }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <div class="filter-title">状态</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.ledgerStatus }">
            <button
              v-for="item in getVisibleFilterOptions('ledgerStatus', statusFilterOptions)"
              :key="`filter-status-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(ledgerFilter.statuses, item.value) }"
              @click="toggleFilterValue(ledgerFilter.statuses, item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
      </div>
      <div class="grid ledger-filter-inputs">
        <label>
          编号关键词
          <input v-model="ledgerFilter.keyword" type="text" placeholder="输入编号关键字" />
        </label>
        <div class="ledger-filter-actions">
          <button type="button" class="small" @click="resetLedgerFilter">重置筛选</button>
        </div>
      </div>
      <div v-if="loading">加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>部门</th>
              <th>类型</th>
              <th>状态</th>
              <th>替代</th>
              <th>被替代</th>
              <th>依赖</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in pagedFilteredLedger" :key="item.id">
              <tr class="ledger-main-row" :class="{ expanded: isLedgerExpanded(item.id) }" @click="toggleLedgerDetail(item.id)">
                <td>{{ item.fullCode }}</td>
                <td>{{ item.deptCode }}</td>
                <td>{{ item.docType }}</td>
                <td>{{ statusDisplayLabel(item) }}</td>
                <td class="icon-cell"><span class="relation-dot" :class="{ active: hasRelation(item.replacesCode) }">{{ hasRelation(item.replacesCode) ? '●' : '○' }}</span></td>
                <td class="icon-cell"><span class="relation-dot" :class="{ active: hasRelation(item.replacedByCode) }">{{ hasRelation(item.replacedByCode) ? '●' : '○' }}</span></td>
                <td class="icon-cell"><span class="relation-dot" :class="{ active: hasRelation(item.dependencyCodes) }">{{ hasRelation(item.dependencyCodes) ? '●' : '○' }}</span></td>
                <td>{{ formatDateTime(item.createdAt) }}</td>
              </tr>
              <tr v-if="isLedgerExpanded(item.id)" class="ledger-detail-row">
                <td colspan="8">
                  <div class="ledger-detail-wrap">
                    <p><strong>替代：</strong>{{ relationText(item.replacesCode) }}</p>
                    <p><strong>被替代：</strong>{{ relationText(item.replacedByCode) }}</p>
                    <p><strong>依赖：</strong>{{ relationText(item.dependencyCodes) }}</p>
                    <p><strong>绑定文章标题：</strong>{{ item.articleTitle || '-' }}</p>
                    <p>
                      <strong>绑定文章链接：</strong>
                      <a v-if="item.articleLink" :href="item.articleLink" target="_blank" rel="noopener noreferrer">{{ item.articleLink }}</a>
                      <span v-else>-</span>
                    </p>
                    <p><strong>绑定文章发布日期：</strong>{{ item.articlePublishedDate || '-' }}</p>
                    <p><strong>生效日期：</strong>{{ item.effectiveDate || '-' }}</p>
                    <p><strong>替代日期：</strong>{{ item.supersededDate || '-' }}</p>
                    <p><strong>作废日期：</strong>{{ item.voidDate || '-' }}</p>
                    <p><strong>备注：</strong>{{ item.remark || '-' }}</p>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="filteredLedger.length === 0">
              <td colspan="8">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="filteredLedger.length > 0" class="pager">
        <div class="pager-left">
          <span>共 {{ filteredLedger.length }} 条</span>
          <div class="pager-size">
            <span>每页</span>
            <select :value="ledgerPagination.pageSize" @change="onLedgerPageSizeChange">
              <option v-for="size in ledgerPageSizes" :key="`ledger-size-${size}`" :value="size">{{ size }}</option>
            </select>
            <span>条</span>
          </div>
        </div>
        <div class="pager-right">
          <button type="button" class="small ghost" :disabled="ledgerPagination.page <= 1" @click="changeLedgerPage(ledgerPagination.page - 1)">上一页</button>
          <span class="pager-info">{{ ledgerPagination.page }} / {{ ledgerTotalPages }}</span>
          <button type="button" class="small ghost" :disabled="ledgerPagination.page >= ledgerTotalPages" @click="changeLedgerPage(ledgerPagination.page + 1)">下一页</button>
        </div>
      </div>
    </section>

    <section v-if="currentMode === 'logs' && (canView || canManage)" class="card">
      <h2>操作日志</h2>
      <div class="filter-panel log-filter">
        <div class="filter-row">
          <div class="filter-title">操作类型</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.logAction }">
            <button
              v-for="item in getVisibleFilterOptions('logAction', actionFilterOptions)"
              :key="`log-action-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(logFilter.actions, item.value) }"
              @click="toggleFilterValue(logFilter.actions, item.value)"
            >
              {{ item.label }}
            </button>
            <button
              v-if="getHiddenFilterCount('logAction', actionFilterOptions) > 0"
              type="button"
              class="filter-pill expand"
              @click="toggleFilterRowExpand('logAction')"
            >
              {{ expandedFilterRows.logAction ? '收起' : `+${getHiddenFilterCount('logAction', actionFilterOptions)}` }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <div class="filter-title">操作人</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.logOperator }">
            <button
              v-for="item in getVisibleFilterOptions('logOperator', operatorFilterOptions)"
              :key="`log-operator-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(logFilter.operators, item.value) }"
              @click="toggleFilterValue(logFilter.operators, item.value)"
            >
              {{ item.label }}
            </button>
            <button
              v-if="getHiddenFilterCount('logOperator', operatorFilterOptions) > 0"
              type="button"
              class="filter-pill expand"
              @click="toggleFilterRowExpand('logOperator')"
            >
              {{ expandedFilterRows.logOperator ? '收起' : `+${getHiddenFilterCount('logOperator', operatorFilterOptions)}` }}
            </button>
          </div>
        </div>
      </div>
      <div class="grid log-filter-inputs">
        <label>
          编号关键词
          <input v-model="logFilter.keyword" type="text" placeholder="输入编号关键字" />
        </label>
        <label>
          开始日期
          <input v-model="logFilter.start" type="date" />
        </label>
        <label>
          结束日期
          <input v-model="logFilter.end" type="date" />
        </label>
        <div class="log-filter-actions">
          <button type="button" class="small" @click="resetLogFilter">重置筛选</button>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>操作</th>
              <th>编号</th>
              <th>操作人</th>
              <th>时间</th>
              <th>摘要</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in pagedFilteredLogs" :key="item.id">
              <tr class="log-main-row" :class="{ expanded: isLogExpanded(item.id) }" @click="toggleLogDetail(item.id)">
              <td>{{ actionLabel(item.action) }}</td>
              <td>{{ item.targetCode || '-' }}</td>
              <td>{{ item.operator || '-' }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
              <td>{{ item.detail || '-' }}</td>
              </tr>
              <tr v-if="isLogExpanded(item.id)" class="log-detail-row">
                <td colspan="5">
                  <div class="log-detail-wrap">
                    <div v-if="!item.changes || item.changes.length === 0" class="log-detail-empty">暂无字段级明细</div>
                    <table v-else class="log-detail-table">
                      <thead>
                        <tr>
                          <th>字段</th>
                          <th>原值</th>
                          <th>新值</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(change, idx) in item.changes" :key="`${item.id}-change-${idx}`">
                          <td>{{ change.field }}</td>
                          <td>{{ change.fromValue || '-' }}</td>
                          <td>{{ change.toValue || '-' }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="filteredLogs.length === 0">
              <td colspan="5">暂无日志</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="filteredLogs.length > 0" class="pager">
        <div class="pager-left">
          <span>共 {{ filteredLogs.length }} 条</span>
          <div class="pager-size">
            <span>每页</span>
            <select :value="logPagination.pageSize" @change="onLogPageSizeChange">
              <option v-for="size in logPageSizes" :key="`log-size-${size}`" :value="size">{{ size }}</option>
            </select>
            <span>条</span>
          </div>
        </div>
        <div class="pager-right">
          <button type="button" class="small ghost" :disabled="logPagination.page <= 1" @click="changeLogPage(logPagination.page - 1)">上一页</button>
          <span class="pager-info">{{ logPagination.page }} / {{ logTotalPages }}</span>
          <button type="button" class="small ghost" :disabled="logPagination.page >= logTotalPages" @click="changeLogPage(logPagination.page + 1)">下一页</button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
@import '../styles/admin-kit/index.css';
</style>

