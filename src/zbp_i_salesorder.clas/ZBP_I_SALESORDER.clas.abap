CLASS ZBP_I_SALESORDER DEFINITION PUBLIC ABSTRACT FINAL
  FOR BEHAVIOR OF ZI_I_SALESORDER.
ENDCLASS.

CLASS ZBP_I_SALESORDER IMPLEMENTATION.
ENDCLASS.

*----------------------------------------------------------------------*
* Local handler classes
*----------------------------------------------------------------------*

CLASS lhc_i_salesorderitem DEFINITION INHERITING FROM cl_abap_behavior_handler.
  PRIVATE SECTION.
    METHODS Test_action FOR MODIFY
      IMPORTING keys FOR ACTION i_salesorderitem~Test_action RESULT result.
ENDCLASS.

CLASS lhc_i_salesorderitem IMPLEMENTATION.

  METHOD Test_action.

    " Read relevant i_salesorderitem instances
    READ ENTITIES OF ZI_I_SALESORDER IN LOCAL MODE
      ENTITY i_salesorderitem
        ALL FIELDS WITH CORRESPONDING #( keys )
      RESULT DATA(lt_i_salesorderitem).

    " TODO: Implement business logic for action 'Test_action'
    LOOP AT lt_i_salesorderitem ASSIGNING FIELD-SYMBOL(<entity>).
      " Example: <entity>-Status = 'NEW_STATUS'.
    ENDLOOP.

    " Modify entities with updated data
    MODIFY ENTITIES OF ZI_I_SALESORDER IN LOCAL MODE
      ENTITY i_salesorderitem
        UPDATE FIELDS ( viewType: )
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
