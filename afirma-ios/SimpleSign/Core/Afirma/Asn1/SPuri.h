/*
 * Generated by asn1c-0.9.22 (http://lionet.info/asn1c)
 * From ASN.1 module "SIGNEDDATA"
 * 	found in "SIGNEDDATA.asn1"
 * 	`asn1c -S/skeletons`
 */

#ifndef	_SPuri_H_
#define	_SPuri_H_


#include "asn_application.h"

/* Including external dependencies */
#include "IA5String.h"

#ifdef __cplusplus
extern "C" {
#endif

/* SPuri */
typedef IA5String_t	 SPuri_t;

/* Implementation */
extern asn_TYPE_descriptor_t asn_DEF_SPuri;
asn_struct_free_f SPuri_free;
asn_struct_print_f SPuri_print;
asn_constr_check_f SPuri_constraint;
ber_type_decoder_f SPuri_decode_ber;
der_type_encoder_f SPuri_encode_der;
xer_type_decoder_f SPuri_decode_xer;
xer_type_encoder_f SPuri_encode_xer;

#ifdef __cplusplus
}
#endif

#endif	/* _SPuri_H_ */
