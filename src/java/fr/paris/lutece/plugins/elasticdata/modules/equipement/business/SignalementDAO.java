/*
 * Copyright (c) 2002-2017, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.elasticdata.modules.equipement.business;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

import fr.paris.lutece.plugins.elasticdata.business.DataObject;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.sql.DAOUtil;

/**
 * SignalementDAO
 */
public class SignalementDAO
{
    private static final String PROPERTY_CANAL_PREFIX = "elasticdata-equipement.canal.";
    private static final String CANAL_S = "S";
    private static final String CANAL_B = "B";
    private static final String CANAL_A = "A";
    private static final String CANAL_G = "G";
    private static final String CANAL_UNKOWN = "unkown";
    private static final String[] CANAUX = { CANAL_S , CANAL_B, CANAL_A, CANAL_G };

    private static final String SQL_QUERY_SELECTALL = "SELECT s.id_signalement, "
            + " CONCAT(prefix, annee, mois, numero) as \"numero_anomalie\", "
            + " prefix as canal, ws.name as statut,  ST_X(geom) as \"lon\", ST_Y(geom) as \"lat\", "
            + " stsa.alias_mobile as \"description_public\", "
            + " to_timestamp((substring((date_creation ||'') from 0 for 11) || ' ' || substring((heure_creation || '') from 12 for 8)), 'YYYY-MM-DD HH24:MI:SS') as \"date_creation\", "
            + " date_min.min as \"date_prise_en_compte\","
            + " date_max.max as \"date_cloture\","
            + " ts.libelle as \"categorie\", tss.libelle as \"categorie_parent\", tsss.libelle as \"categorie_grandparent\" "
            + " FROM equipement_signalement s "
            + " INNER JOIN equipement_adresse a ON s.id_signalement = a.fk_id_signalement "
            + " INNER JOIN equipement_type_signalement ts ON ts.id_type_signalement = s.fk_id_type_signalement "
            + " LEFT JOIN equipement_type_signalement tss ON ts.fk_id_type_signalement = tss.id_type_signalement "
            + " LEFT JOIN equipement_type_signalement tsss ON tss.fk_id_type_signalement = tsss.id_type_signalement "
            + " LEFT JOIN equipement_type_signalement_alias stsa ON stsa.fk_id_type_signalement = s.fk_id_type_signalement "
            + " INNER JOIN workflow_resource_workflow wrw ON  s.id_signalement=wrw.id_resource AND wrw.resource_type='SIGNALEMENT_SIGNALEMENT' "
            + " INNER JOIN workflow_state ws ON wrw.id_state=ws.id_state"
            + " LEFT JOIN ("
            + "     select ss.id_signalement, min(wrh.creation_date) from equipement_signalement ss inner join workflow_resource_history wrh on ss.id_signalement = wrh.id_resource and wrh.user_access_code != 'auto' group by ss.id_signalement order by ss.id_signalement"
            + " ) date_min on s.id_signalement = date_min.id_signalement"
            + " LEFT JOIN ("
            + "     select ss.id_signalement, max(wrh.creation_date) from equipement_signalement ss inner join workflow_resource_workflow wrw on ss.id_signalement = wrw.id_resource inner join workflow_resource_history wrh on ss.id_signalement = wrh.id_resource where wrw.id_state in (10,11) group by ss.id_signalement  order by ss.id_signalement"
            + " ) date_max on s.id_signalement = date_max.id_signalement";

    public Collection<DataObject> selectSignalementDataObjectsList( Plugin plugin )
    {
        Collection<DataObject> listSignalementDataObjects = new ArrayList<>( );
        DAOUtil daoUtil = new DAOUtil( SQL_QUERY_SELECTALL, plugin );
        daoUtil.executeQuery( );

        while ( daoUtil.next( ) )
        {
            SignalementDataObject signalement = new SignalementDataObject( );

            signalement.setId( daoUtil.getInt( "id_signalement" ) );
            signalement.setNumeroAnomalie( daoUtil.getString( "numero_anomalie" ) );
            signalement.setCanal( translateCanal( daoUtil.getString( "canal" )));
            signalement.setStatut( daoUtil.getString( "statut" ));
            signalement.setDescriptionPublic( daoUtil.getString( "description_public" ) );
            Location location = new Location();
            location.setLon( daoUtil.getString( "lon" ) );
            location.setLat( daoUtil.getString( "lat" ) );
            signalement.setLocation( location );
            long lDateCreation = daoUtil.getTimestamp( "date_creation" ).getTime( );
            signalement.setTimestamp( lDateCreation );
            Timestamp tTimestampPriseEnCompte = daoUtil.getTimestamp( "date_prise_en_compte" );
            if (tTimestampPriseEnCompte != null)
            {
                signalement.setTimestampPriseEnCompte( tTimestampPriseEnCompte.getTime( ) );
            }
            Timestamp tTimestampCloture = daoUtil.getTimestamp( "date_cloture" );
            if (tTimestampCloture != null)
            {
                signalement.setTimestampCloture( tTimestampCloture.getTime( ) );
            }
            signalement.setCategory( daoUtil.getString( "categorie" ) );

            listSignalementDataObjects.add( signalement );
        }

        daoUtil.free( );

        return listSignalementDataObjects;
    }
    
    
    private String translateCanal( String strSource )
    {
        for( String strCanal : CANAUX )
        {
            if( strSource.equalsIgnoreCase( strCanal ) )
            {
                return AppPropertiesService.getProperty( PROPERTY_CANAL_PREFIX + strCanal );
            }
        }
        return AppPropertiesService.getProperty( PROPERTY_CANAL_PREFIX + CANAL_UNKOWN );
    }

}
