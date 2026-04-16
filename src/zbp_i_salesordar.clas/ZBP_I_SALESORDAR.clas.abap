CLASS ZBP_I_SALESORDAR DEFINITION PUBLIC ABSTRACT FINAL
  FOR BEHAVIOR OF ZI_I_SALESORDAR.
ENDCLASS.

CLASS ZBP_I_SALESORDAR IMPLEMENTATION.
ENDCLASS.

*----------------------------------------------------------------------*
* Local handler classes
*----------------------------------------------------------------------*

CLASS lhc_i_salesorderitem DEFINITION INHERITING FROM cl_abap_behavior_handler.
  PRIVATE SECTION.
    METHODS test001 FOR MODIFY
      IMPORTING keys FOR ACTION i_salesorderitem~test001 RESULT result.
ENDCLASS.

CLASS lhc_i_salesorderitem IMPLEMENTATION.

  METHOD test001.

    " Read relevant i_salesorderitem instances
    READ ENTITIES OF ZI_I_SALESORDAR IN LOCAL MODE
      ENTITY i_salesorderitem
        ALL FIELDS WITH CORRESPONDING #( keys )
      RESULT DATA(lt_i_salesorderitem).

    " TODO: Implement business logic for action 'test001'
    LOOP AT lt_i_salesorderitem ASSIGNING FIELD-SYMBOL(<entity>).
      " Example: <entity>-Status = 'NEW_STATUS'.
    ENDLOOP.

    " Modify entities with updated data
    MODIFY ENTITIES OF ZI_I_SALESORDAR IN LOCAL MODE
      ENTITY i_salesorderitem
        UPDATE FIELDS ( SalesOrderItemUUID )
        WITH VALUE #( FOR entity IN lt_i_salesorderitem
          ( %tky = entity-%tky
            " TODO: Set field values here
          ) )
      FAILED failed
      REPORTED reported.

    " Fill the result
    result = VALUE #( FOR entity IN lt_i_salesorderitem
      ( %tky = entity-%tky
        %param = entity ) ).

  ENDMETHOD.
ENDCLASS.
