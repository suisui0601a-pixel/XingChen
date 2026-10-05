import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { UsageCostSummary, decimalDisplay, type CostSummary } from './UsageCostSummary'
import { UsageConsolePage } from './UsageConsolePage'
const {api}=vi.hoisted(()=>({api:vi.fn()}))
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),api}))
afterEach(()=>{cleanup();api.mockReset()})
const base:CostSummary={costsByCurrency:{USD:{estimatedCost:'1.200000000000',pricedUsageCount:1,pricedTokens:100}},usageCount:1,pricedUsageCount:1,unpricedUsageCount:0,totalTokens:100,pricedTokens:100,unpricedTokens:0,pricingCoverage:'COMPLETE',currencyCount:1,mixedCurrency:false,estimatedCost:'1.200000000000',currency:'USD',totalUnavailableReason:null}
describe('Usage cost accounting',()=>{
 it('single currency complete exposes an estimated total',()=>{
  render(<UsageCostSummary cost={base} language="en-US"/>);expect(screen.getByTestId('estimated-total')).toHaveTextContent('Estimated total cost: USD 1.2');expect(screen.getByTestId('pricing-coverage')).toHaveTextContent('Complete')
 })
 it('mixed complete shows separate currencies and no combined total',()=>{
  render(<UsageCostSummary cost={{...base,currencyCount:2,mixedCurrency:true,estimatedCost:null,currency:null,costsByCurrency:{...base.costsByCurrency,CNY:{estimatedCost:'3.4',pricedUsageCount:1,pricedTokens:200}}}} language="en-US"/>);expect(screen.queryByTestId('estimated-total')).not.toBeInTheDocument();expect(screen.getByTestId('cost-USD')).toHaveTextContent('USD 1.2');expect(screen.getByTestId('cost-CNY')).toHaveTextContent('CNY 3.4');expect(screen.getByText(/No combined total/)).toBeInTheDocument()
 })
 it('partial single currency shows priced subtotal and retained unpriced tokens',()=>{
  render(<UsageCostSummary cost={{...base,pricingCoverage:'PARTIAL',unpricedUsageCount:2,unpricedTokens:300,estimatedCost:null}} language="en-US"/>);expect(screen.queryByTestId('estimated-total')).not.toBeInTheDocument();expect(screen.getByText('Priced subtotal')).toBeInTheDocument();expect(screen.getByTestId('unpriced-usage')).toHaveTextContent('Unpriced usage: 2 · Unpriced tokens: 300');expect(screen.getByTestId('pricing-coverage')).toHaveTextContent('Partial')
 })
 it('all unpriced is none rather than an invented zero',()=>{
  render(<UsageCostSummary cost={{...base,costsByCurrency:{},pricingCoverage:'NONE',unpricedUsageCount:1,unpricedTokens:100,pricedUsageCount:0,pricedTokens:0,currencyCount:0,estimatedCost:null,currency:null}} language="en-US"/>);expect(screen.getByTestId('pricing-coverage')).toHaveTextContent('None');expect(screen.queryByTestId('cost-USD')).not.toBeInTheDocument();expect(screen.queryByTestId('estimated-total')).not.toBeInTheDocument();expect(screen.getByText(/Missing pricing is not zero cost/)).toBeInTheDocument()
 })
 it('explicit zero is a valid priced total',()=>{
  render(<UsageCostSummary cost={{...base,estimatedCost:'0.000000000000',costsByCurrency:{USD:{...base.costsByCurrency.USD,estimatedCost:'0'}}}} language="en-US"/>);expect(screen.getByTestId('estimated-total')).toHaveTextContent('USD 0');expect(screen.queryByTestId('unpriced-usage')).not.toBeInTheDocument()
 })
 it('Chinese mixed partial preserves both warnings and historical semantics',()=>{
  render(<UsageCostSummary cost={{...base,mixedCurrency:true,currencyCount:2,pricingCoverage:'PARTIAL',unpricedUsageCount:1,unpricedTokens:7,estimatedCost:null}} language="zh-CN"/>);expect(screen.getByTestId('pricing-coverage')).toHaveTextContent('定价覆盖率: 部分');expect(screen.getByText(/多币种费用不可直接合并/)).toBeInTheDocument();expect(screen.getByTestId('unpriced-usage')).toHaveTextContent('未定价用量: 1');expect(screen.getByText(/历史用量/)).toBeInTheDocument();expect(screen.queryByText(/Pricing coverage|Priced subtotal|No combined/)).not.toBeInTheDocument()
 })
 it('decimal display does not round through binary floating point',()=>{
  expect(decimalDisplay('12345678901234567890.000000000001','en-US')).toBe('12,345,678,901,234,567,890.000000000001');expect(decimalDisplay('0.000000000001','zh-CN')).toBe('0.000000000001')
 })
 it('pricing editor preserves decimal strings and explicit missing rate, refreshes coverage after save',async()=>{
  let saved=false
  api.mockImplementation(async(path:string,options?:RequestInit)=>{
   if(options?.method==='PATCH'){saved=true;return {revision:1}}
   if(path==='/api/pricing')return {revision:saved?1:0,rows:[{provider:'p',model:'m',currency:'USD',input:'0.123456789012345678',cacheHit:'0',cacheMiss:'0',output:'1',reasoning:'0'}]}
   if(path.startsWith('/api/usage/summary'))return {summary:{},cost:saved?base:{...base,pricingCoverage:'PARTIAL',unpricedUsageCount:1,estimatedCost:null}}
   if(path.startsWith('/api/usage/breakdown'))return {providers:[],conversations:[]}
   return []
  })
  render(<UsageConsolePage language="en-US"/>);await screen.findByTestId('pricing-coverage');fireEvent.change(screen.getByLabelText('m Cache hit (cacheHit)'),{target:{value:''}});fireEvent.click(screen.getByRole('button',{name:'Save rates'}));await waitFor(()=>expect(screen.getByTestId('pricing-coverage')).toHaveTextContent('Complete'))
  const write=api.mock.calls.find(([,options])=>options?.method==='PATCH');const row=JSON.parse(write![1].body).rows[0];expect(row.input).toBe('0.123456789012345678');expect(row.cacheHit).toBeNull()
 })
})
