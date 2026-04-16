@AccessControl.authorizationCheck: #NOT_REQUIRED
@EndUserText.label: 'i_salesorder'
define root view entity ZI_I_SALESORDER
  as select from i_salesorder
  composition [0..*] of ZI_I_SALESORDERITEM as _i_salesorderitem
{
      viewType:,
      lifecycle.contract.type:,

  _i_salesorderitem
}
