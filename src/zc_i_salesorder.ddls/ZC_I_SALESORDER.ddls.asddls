@AccessControl.authorizationCheck: #NOT_REQUIRED
@EndUserText.label: 'i_salesorder'
define root view entity ZC_I_SALESORDER
  provider contract transactional_query
  as projection on ZI_I_SALESORDER
{
      viewType:,
      lifecycle.contract.type:,

  _i_salesorderitem : redirected to composition child ZC_I_SALESORDERITEM
}
