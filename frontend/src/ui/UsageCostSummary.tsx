import type { Language } from './i18n'

export type CostSummary = {
 costsByCurrency: Record<string, {estimatedCost:string|number;pricedUsageCount:number;pricedTokens:number}>
 usageCount:number; pricedUsageCount:number; unpricedUsageCount:number
 totalTokens:number; pricedTokens:number; unpricedTokens:number
 pricingCoverage:'COMPLETE'|'PARTIAL'|'NONE';currencyCount:number;mixedCurrency:boolean
 estimatedCost:string|number|null;currency:string|null;totalUnavailableReason:string|null
}
const text=(lang:Language,zh:string,en:string)=>lang==='zh-CN'?zh:en
// Preserve decimal digits from the API; no floating monetary calculation/rounding.
export function decimalDisplay(value:string|number,lang:Language) {
 const raw=String(value)
 const match=/^(\d+)(?:\.(\d+))?$/.exec(raw)
 if(!match)return raw
 const integer=BigInt(match[1]).toLocaleString(lang)
 const fraction=match[2]?.replace(/0+$/,'')
 return integer+(fraction?'.'+fraction:'')
}
export function UsageCostSummary({cost,language:lang}:{cost:CostSummary;language:Language}) {
 const coverage=cost.pricingCoverage==='COMPLETE'?text(lang,'完整','Complete'):cost.pricingCoverage==='PARTIAL'?text(lang,'部分','Partial'):text(lang,'无','None')
 const total=cost.currencyCount===1&&cost.pricingCoverage==='COMPLETE'&&cost.estimatedCost!==null
 return <section className="settings-section" aria-label={text(lang,'费用核算','Cost accounting')}>
  <h2>{text(lang,'估算费用','Estimated cost')}</h2>
  <p data-testid="pricing-coverage">{text(lang,'定价覆盖率','Pricing coverage')}: {coverage}</p>
  <p>{text(lang,'已定价用量','Priced usage')}: {cost.pricedUsageCount} / {cost.usageCount} · {text(lang,'已定价 token','Priced tokens')}: {cost.pricedTokens} / {cost.totalTokens}</p>
  {total?<p data-testid="estimated-total">{text(lang,'估算总费用','Estimated total cost')}: {cost.currency} {decimalDisplay(cost.estimatedCost!,lang)}</p>:<p>{text(lang,'已定价小计','Priced subtotal')}</p>}
  {Object.entries(cost.costsByCurrency).map(([currency,v])=><p data-testid={`cost-${currency}`} key={currency}>{currency} {decimalDisplay(v.estimatedCost,lang)} · {text(lang,'已定价记录','Priced records')}: {v.pricedUsageCount} · token: {v.pricedTokens}</p>)}
  {cost.mixedCurrency&&<p role="note">{text(lang,'多币种费用不可直接合并；不提供合并总额。','Costs in different currencies are not combined. No combined total.')}</p>}
  {cost.pricingCoverage!=='COMPLETE'&&<p role="note" data-testid="unpriced-usage">{text(lang,'未定价用量','Unpriced usage')}: {cost.unpricedUsageCount} · {text(lang,'未定价 token','Unpriced tokens')}: {cost.unpricedTokens}. {text(lang,'缺少费率不等于零费用。','Missing pricing is not zero cost.')}</p>}
  <p>{text(lang,'费用仅为估算，不是账单；不换算汇率。价格修改会按当前费率重新估算历史用量。','Costs are estimates, not billing; no FX conversion. Changing pricing may change historical estimates.')}</p>
 </section>
}
